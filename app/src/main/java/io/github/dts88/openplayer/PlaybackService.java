package io.github.dts88.openplayer;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.OptIn;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

@OptIn(markerClass = UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private PlayerEngine engine;
    private MediaSession session;

    private final PlayerEngine.Listener listener = new PlayerEngine.Listener() {
        @Override
        public void onPlayerReplaced(ExoPlayer player) {
            if (session != null) session.setPlayer(new SessionPlayer(player, engine));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        engine = PlayerEngine.get(this);
        MediaSession.Builder b = new MediaSession.Builder(this, new SessionPlayer(engine.player(), engine))
                .setId("op-session-7x4");
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) {
            b.setSessionActivity(PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        session = b.build();
        engine.addListener(listener);
        setMediaNotificationProvider(new DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel)
                .build());
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player p = engine.player();
        if (!p.getPlayWhenReady() || p.getMediaItemCount() == 0 || p.getPlaybackState() == Player.STATE_ENDED
                || p.getPlaybackState() == Player.STATE_IDLE) {
            engine.savePosition();
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        engine.removeListener(listener);
        if (session != null) {
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    private static final class SessionPlayer extends ForwardingPlayer {
        private final PlayerEngine engine;

        SessionPlayer(Player player, PlayerEngine engine) {
            super(player);
            this.engine = engine;
        }

        @Override
        public void seekToNext() {
            engine.next();
        }

        @Override
        public void seekToNextMediaItem() {
            engine.next();
        }

        @Override
        public void seekToPrevious() {
            engine.previous();
        }

        @Override
        public void seekToPreviousMediaItem() {
            engine.previous();
        }

        @Override
        public boolean hasNextMediaItem() {
            return engine.hasNext();
        }

        @Override
        public boolean hasPreviousMediaItem() {
            return true;
        }

        @Override
        public boolean isCommandAvailable(@Player.Command int command) {
            if (isSkip(command) && getMediaItemCount() > 0) return true;
            return super.isCommandAvailable(command);
        }

        @Override
        public Commands getAvailableCommands() {
            Commands c = super.getAvailableCommands();
            if (getMediaItemCount() == 0) return c;
            return c.buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build();
        }

        private static boolean isSkip(int command) {
            return command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                    || command == COMMAND_SEEK_TO_PREVIOUS || command == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
        }
    }
}
