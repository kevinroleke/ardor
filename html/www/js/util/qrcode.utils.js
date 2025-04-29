/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

var NRS = (function (NRS) {

    NRS.scanQRCode = function(readerId, callback) {
        if (!NRS.isScanningAllowed()) {
            $.growl($.t("scanning_not_allowed"));
            return;
        }
        NRS.logConsole("scan using desktop browser");
        html5Scan(readerId, callback);
    };

    function html5Scan(readerId, callback) {
        var reader = $("#" + readerId);
        if (reader.is(':visible')) {
            reader.fadeOut();
            NRS.stopScanQRCode();
            return;
        }
        reader.empty();
        reader.fadeIn();
        html5_qrcode(reader,
            function (data) {
                callback(data);
                reader.hide();
                NRS.stopScanQRCode();
            },
            function (error) {
                NRS.logConsole("Scan error: " + error === undefined ? "(empty)" : error.message);
                reader.hide();
                if (NRS.isCameraAccessSupported()) {
                    if (error !== undefined && error.type !== undefined) {
                        switch(error.type) {
                            case 'NotAllowedError':
                                $.growl($.t("no_allowed_cameras"));
                                break;
                            case 'NotFoundError':
                                $.growl($.t("no_cameras_found"));
                                break;
                            case 'NotReadableError':
                                $.growl($.t("video_hardware_error"));
                                break;
                            default:
                                $.growl($.t("video_error"));
                        }
                    } else {
                        $.growl($.t("video_error"));
                    }
                } else {
                    $.growl($.t("scan_not_supported"));
                }
                NRS.stopScanQRCode();
            }
        );
    }

    var scanner;

    async function html5_qrcode(currentElem, qrcodeSuccess, qrcodeError) {
        var vidElem = $('<video></video>').addClass('qr').appendTo(currentElem);
        var video = vidElem[0];

        if (NRS.isAndroidWebView()) {
            try {
                await navigator.mediaDevices.getUserMedia({ video: true });
            } catch(ignore) {
            }
        }

        Instascan.Camera.getCameras().then(function (cameras) {
            if (cameras.length > 0) {
                let mirrorElem = $('<label class="checkbox-inline"><input type="checkbox"/>' +
                        '<span data-i18n="flip_horizontally">Flip image horizontally</span></label><br/>');
                mirrorElem.prependTo(currentElem);
                if (cameras.length > 1) {
                    $('<br/>').prependTo(currentElem);
                    var selectBox = $('<select/>')
                    for (var camId in cameras) {
                        var cam = cameras[camId];
                        var name = cam.name ? cam.name : "Camera " + camId;
                        $('<option />', {value: camId, text: name}).appendTo(selectBox);
                    }
                    selectBox.val(NRS.deviceSettings.camera_id);
                    selectBox.change(function() {
                        NRS.deviceSettings.camera_id = selectBox.val();
                        NRS.setJSONItem("device_settings", NRS.deviceSettings);
                        scanner.stop();
                        scanner.start(cameras[NRS.deviceSettings.camera_id]);
                    });
                    selectBox.prependTo(currentElem);
                }
                let camera = cameras[NRS.deviceSettings.camera_id];
                let mirrorValue = NRS.deviceSettings.camera_flip_horizontally;
                if (mirrorValue === null) {
                    mirrorValue = camera && camera.name && camera.name.match(/back/i) == null;
                }
                let mirrorCheckBox = mirrorElem.find("input[type='checkbox']");
                mirrorCheckBox.prop("checked", mirrorValue);
                mirrorCheckBox.change(function() {
                    const mirrorValue = $(this).is(':checked');
                    scanner.mirror = mirrorValue;
                    NRS.deviceSettings.camera_flip_horizontally = mirrorValue;
                    NRS.setJSONItem("device_settings", NRS.deviceSettings);
                });
                scanner = new Instascan.Scanner({ video: video, mirror: mirrorValue });
                scanner.addListener('scan', function (content) {
                    qrcodeSuccess(content);
                    scanner.stop();
                });
                scanner.start(cameras[NRS.deviceSettings.camera_id]);
            } else {
                qrcodeError();
                NRS.stopScanQRCode();
            }
        }).catch(function(e) {
            qrcodeError(e);
            NRS.stopScanQRCode();
        });
    }

    NRS.stopScanQRCode = function() {
        if (scanner) {
            scanner.stop();
        }
    };

    return NRS;
}(NRS || {}));
