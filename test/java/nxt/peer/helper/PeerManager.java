package nxt.peer.helper;

import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class PeerManager {

    public void removeAll(Predicate<Peer> filter, boolean includeHidden) {
        for (Peer peer : Peers.getAllPeers()) {
            if (filter.test(peer)) {
                if (peer.getState() == Peer.State.CONNECTED) {
                    peer.disconnectPeer();
                }
                Peers.removePeer(peer);
            }
        }
    }

    public Peer connect(String address) {
        Peer newPeer = Peers.findOrCreatePeer(address, true);
        if (newPeer != null) {
            Peers.addPeer(newPeer);
            if (newPeer.getState() != Peer.State.CONNECTED && newPeer.getAnnouncedAddress() != null) {
                newPeer.connectPeer();
            }
        }
        return newPeer;
    }

    public Peer connect(Peer peer) {
        Peers.addPeer(peer);
        peer.connectPeer();
        return peer;
    }

    public Peer addPeer(String address) {
        Peer newPeer = Peers.findOrCreatePeer(address, true);
        if (newPeer != null) {
            Peers.addPeer(newPeer);
        }
        return newPeer;
    }

    public void addListener(Listener<Peer> listener, Peers.Event event) {
        Peers.addListener(listener, event);
    }

    public void removeListener(Listener<Peer> listener, Peers.Event event) {
        Peers.removeListener(listener, event);
    }

    public int getNumPeers() {
        return Peers.getAllPeers().size();
    }

    public int getNumConnected() {
        return Peers.getConnectedPeersCount();
    }

    public List<Peer> getAllPeers() {
        return new ArrayList<>(Peers.getAllPeers());
    }

}
