#!/bin/bash

log_msg()
{
  echo -e "$@"
  logger "$@"
}

on_error()
{
  log_msg "$@"
  exit 1
}

log_msg "ramdisk started: $*"

cd "$(dirname "$0")/.." || on_error "Failed to cd $(dirname "$0")/.."

PRODUCT_NAME=Ardor

PRODUCT_DIR="$( pwd -P )"

THIS_SCRIPT=$PRODUCT_DIR/contrib/$(basename "$0")

MOUNT_POINT=/mnt/ardor_ramdisk

MAX_BACKUP_DBS=3

BASE_LOG_NAME=ardor

if grep -e "^nxt.isTestnet=true$" "${PRODUCT_DIR}/conf/nxt.properties" > /dev/null; then
  IS_TESTNET=1
  BACKUPS_DIR=$PRODUCT_DIR/ramdisk-backups/testnet
else
  IS_TESTNET=0
  BACKUPS_DIR=$PRODUCT_DIR/ramdisk-backups/mainnet
fi
log_msg "IS_TESTNET=$IS_TESTNET"

if [ $IS_TESTNET -eq 1 ]; then
  RAMDISK_SIZE=7500M
else
  RAMDISK_SIZE=7400M
fi

if [ $IS_TESTNET -eq 1 ]; then
  DB_DIR=nxt_test_db
  DB_FULL_PATH=$MOUNT_POINT/$DB_DIR/nxt
else
  DB_DIR=nxt_db
  DB_FULL_PATH=$MOUNT_POINT/$DB_DIR/nxt
fi

help()
{
  cat << EOM
Parameters:

--init:   If a tmpfs is not mounted at $MOUNT_POINT, tries to mount it
          (this requires superuser and will ask for password if necessary).
          Then extracts the latest db backup from ${BACKUPS_DIR} to
          $MOUNT_POINT.
          It also checks if $PRODUCT_NAME is configured correctly, so run it first
          and follow the instructions.
          Additionally if "start" is provided as second argument, starts $PRODUCT_NAME
          in daemon mode after the extraction is complete.
          To execute at startup with cron: 'crontab -e' and add the line
          @reboot $THIS_SCRIPT --init start
          To keep the mount after reboot: edit /etc/fstab and add a line
          $PRODUCT_NAME    $MOUNT_POINT     tmpfs     defaults,size=$RAMDISK_SIZE     0 0

--backup: Stops $PRODUCT_NAME and creates a backup of the ramdisk contents to ${BACKUPS_DIR}
          Additionally, if "restart" is provided as second argument, starts
          $PRODUCT_NAME in daemon mode after the backup is complete.
          Finally, deletes the databases of old backups. The last $MAX_BACKUP_DBS
          databases are preserved and also all logs.
          This command must be run regularly and doesn't need root.
          To run it e.g. at 00:00 on every 3rd day-of-month: 'crontab -e' and
          add the line
          0 0 */3 * * $THIS_SCRIPT --backup restart
EOM
  exit
}

init_ramdisk()
{
  if [ $IS_TESTNET -eq 1 ]; then
    DB_PROPERTY=nxt.testDbDir
  else
    DB_PROPERTY=nxt.dbDir
  fi

  grep -e "^$DB_PROPERTY=$DB_FULL_PATH$" "${PRODUCT_DIR}/conf/nxt.properties" > /dev/null || on_error \
    "$PRODUCT_NAME not configured to write the database on the ramdisk.\n"\
    "Add the following line to ${PRODUCT_DIR}/conf/nxt.properties\n"\
    "$DB_PROPERTY=$DB_FULL_PATH\n"


  if [ ! -d $MOUNT_POINT ]; then
    sudo mkdir $MOUNT_POINT || on_error "Creating directory $MOUNT_POINT failed"
  fi

  if mount|grep "$MOUNT_POINT">/dev/null; then
    log_msg "$MOUNT_POINT is already mounted"
  else
    log_msg "Mounting $MOUNT_POINT"
    sudo mount -t tmpfs -o size=$RAMDISK_SIZE swap $MOUNT_POINT || on_error "Failed to mount $MOUNT_POINT"
  fi

  if [ -d "${BACKUPS_DIR}" ]; then
    BACKUP_DIR=$( find "${BACKUPS_DIR}" -maxdepth 1 -name "????-??-??_??-??-??" | sort | tail -n 1 )
    log_msg "Backup dir: $BACKUP_DIR"
    if [ -d "$BACKUP_DIR" ]; then
      cd "$MOUNT_POINT" || on_error "Failed to cd $MOUNT_POINT"
      log_msg "Extracting $BACKUP_DIR/$DB_DIR.tar.gz to $MOUNT_POINT..."
      tar xvzf "$BACKUP_DIR/$DB_DIR.tar.gz" || on_error "Extraction failed"
      log_msg "Extraction complete"
      cd "$PRODUCT_DIR" || on_error "Failed to cd $PRODUCT_DIR"
    fi
  fi

  if ! grep -e "^java.util.logging.FileHandler.pattern=$MOUNT_POINT/logs/$BASE_LOG_NAME.%g.log$" "${PRODUCT_DIR}/conf/logging.properties" > /dev/null; then
    log_msg "Warning: $PRODUCT_NAME is not configured to write logs on the ramdisk."
    cat << EOM
Add the following lines to ${PRODUCT_DIR}/conf/logging.properties:
java.util.logging.FileHandler.pattern=$MOUNT_POINT/logs/$BASE_LOG_NAME.%g.log
java.util.logging.FileHandler.count=1
EOM
  else
    if [ ! -d $MOUNT_POINT/logs ]; then
      mkdir $MOUNT_POINT/logs || on_error "Creating directory $MOUNT_POINT/logs failed"
      chmod a+w $MOUNT_POINT/logs || on_error "Failed to set write permissions on $MOUNT_POINT/logs"
    fi
  fi

  if [ "$1" = "start" ]; then
    ./run.sh --daemon
  fi
}

backup_db()
{
  mount | grep "$MOUNT_POINT">/dev/null || on_error "Backup failed! $MOUNT_POINT is not mounted"

  ./stop.sh

  BACKUP_DIR=${BACKUPS_DIR}/$(date +%F_%H-%M-%S)
  mkdir -p "$BACKUP_DIR"

  cd $MOUNT_POINT || on_error "Failed to cd $MOUNT_POINT"
  tar -zcvf "$BACKUP_DIR/$DB_DIR.tar.gz" $DB_DIR || on_error "Compressing $DB_DIR failed"
  if [ -d logs ]; then
    tar -zcvf "$BACKUP_DIR/logs.tar.gz" logs || on_error "Compressing the logs failed"
  fi

  cd "$PRODUCT_DIR" || on_error "Failed to cd $PRODUCT_DIR"

  for b in $( find "${BACKUPS_DIR}" -maxdepth 1 -name "????-??-??_??-??-??" | sort -r | tail -n +$(( MAX_BACKUP_DBS + 1)) )
  do
    if [ -f "$b/$DB_DIR.tar.gz" ]; then
      log_msg "Removing old db from $b"
      rm "$b/$DB_DIR.tar.gz"
    fi
  done

  if [ "$1" = "restart" ]; then
    ./run.sh --daemon
  fi
}

case $1 in
--init )
  init_ramdisk "$2"
  ;;
--backup )
  backup_db "$2"
  ;;
* )
  help
  ;;
esac
