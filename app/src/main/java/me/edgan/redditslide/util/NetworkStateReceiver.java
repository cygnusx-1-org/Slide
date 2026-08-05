package me.edgan.redditslide.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/** Created by Carlos on 9/10/2016. */
@NullMarked
public class NetworkStateReceiver extends BroadcastReceiver {

    protected List<NetworkStateReceiverListener> listeners;
    /** Null until the first broadcast arrives, so listeners added before it are not notified. */
    @Nullable protected Boolean connected;

    public NetworkStateReceiver() {
        listeners = new ArrayList<NetworkStateReceiverListener>();
        connected = null;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent.getExtras() == null) return;
        connected = NetworkUtil.isConnected(context);
        notifyStateToAll();
    }

    private void notifyStateToAll() {
        for (NetworkStateReceiverListener listener : listeners) notifyState(listener);
    }

    private void notifyState(NetworkStateReceiverListener listener) {
        if (connected == null) return;

        if (connected) listener.networkAvailable();
        else listener.networkUnavailable();
    }

    public void addListener(NetworkStateReceiverListener l) {
        listeners.add(l);
        notifyState(l);
    }

    public void removeListener(NetworkStateReceiverListener l) {
        listeners.remove(l);
    }

    public interface NetworkStateReceiverListener {
        void networkAvailable();

        void networkUnavailable();
    }
}
