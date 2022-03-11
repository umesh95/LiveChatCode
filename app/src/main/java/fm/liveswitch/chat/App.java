package fm.liveswitch.chat;

import android.content.Context;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fm.liveswitch.AudioStream;
import fm.liveswitch.Channel;
import fm.liveswitch.ChannelClaim;
import fm.liveswitch.Client;
import fm.liveswitch.ClientInfo;
import fm.liveswitch.ClientState;
import fm.liveswitch.ConnectionConfig;
import fm.liveswitch.ConnectionInfo;
import fm.liveswitch.ConnectionState;
import fm.liveswitch.DataChannel;
import fm.liveswitch.DataChannelReceiveArgs;
import fm.liveswitch.DataChannelState;
import fm.liveswitch.DataStream;
import fm.liveswitch.EncodingInfo;
import fm.liveswitch.Future;
import fm.liveswitch.IAction0;
import fm.liveswitch.IAction1;
import fm.liveswitch.Layout;
import fm.liveswitch.License;
import fm.liveswitch.Log;
import fm.liveswitch.LogLevel;
import fm.liveswitch.ManagedConnection;
import fm.liveswitch.ManagedThread;
import fm.liveswitch.ManagedTimer;
import fm.liveswitch.McuConnection;
import fm.liveswitch.MediaSourceState;
import fm.liveswitch.PeerConnection;
import fm.liveswitch.Promise;
import fm.liveswitch.SfuDownstreamConnection;
import fm.liveswitch.SfuUpstreamConnection;
import fm.liveswitch.SimulcastMode;
import fm.liveswitch.StreamDirection;
import fm.liveswitch.Token;
import fm.liveswitch.VideoEncodingConfig;
import fm.liveswitch.VideoLayout;
import fm.liveswitch.VideoSource;
import fm.liveswitch.VideoStream;
import fm.liveswitch.android.Camera2Source;
import fm.liveswitch.android.CameraSource;
import fm.liveswitch.android.LayoutManager;
import layout.TextChatFragment;
import layout.VideoChatFragment;

public class App 
{

    public enum Modes 
    {
        Mcu(1), Sfu(2), Peer(3);

        private final int value;
        Modes(int value) 
        {
            this.value = value;
        }

        public int getValue() 
        {
            return value;
        }
    }

    private final Handler handler;
    private Channel channel = null;
    private McuConnection mcuConnection = null;
    private SfuUpstreamConnection sfuUpstreamConnection = null;
    private HashMap<String, SfuDownstreamConnection> sfuDownstreamConnections = null;
    private HashMap<String, fm.liveswitch.PeerConnection> peerConnections = null;
    private LocalMedia localMedia = null;
    private AecContext aecContext = null;
    private LayoutManager layoutManager = null;
    private VideoLayout videoLayout = null;
    public VideoChatFragment videoChatFragmentLayout = null;
    public HashMap<String, Boolean> contextMenuItemFlag = null;
    public HashMap<String, ManagedConnection> remoteMediaMaps = null;

    private String gatewayUrl = "https://demo.liveswitch.fm:8443/sync";

    // Track whether the user has decided to leave (unregister)
    // If they have not and the client gets into the Disconnected state then we attempt to reregister (reconnect) automatically.
    private boolean unRegistering = false;
    private int reRegisterBackoff = 200;
    private int maxRegisterBackoff = 60000;

    private Modes mode = null;

    private String applicationId = "my-app-id";
    private String userName = null;
    private String channelId = null;
    private String deviceId = fm.liveswitch.Guid.newGuid().toString().replaceAll("-", "");
    private String userId = fm.liveswitch.Guid.newGuid().toString().replaceAll("-", "");
    private String mcuViewId = null;

    private fm.liveswitch.Client client = null;

    private Context context = null;


    private OnReceivedTextListener textListener;
    private boolean usingFrontVideoDevice = true;
    private boolean audioOnly;
    private boolean receiveOnly;
    private boolean enableSimulcast;
    private boolean enableScreenShare;
    boolean enableH264 = false;

    private boolean dataChannelConnected = false;
    ArrayList<DataChannel> dataChannels = new ArrayList<DataChannel>();
    private Object dataChannelLock = new Object(); //synchronize data channel book-keeping (collection may be modified while trying to send messages in SendDataChannelMessage())
    
    ManagedTimer dataChannelsMessageTimer = null;

    public void setUserName(String userName) 
    {
        this.userName = userName;
    }
    public void setChannelId(String cid) 
    {
        this.channelId = cid;
    }
    public void setMode(Modes m) 
    {
        mode = m;
    }
    public Modes getMode() { return mode;};
    public void setAudioOnly(boolean audioOnly) 
    {
        this.audioOnly = audioOnly;
    }
    public void setReceiveOnly(boolean receiveOnly) 
    {
        this.receiveOnly = receiveOnly;
    }
    public void setEnableSimulcast(boolean simulcast) {this.enableSimulcast = simulcast;}
    public boolean getEnableSimulcast() {return this.enableSimulcast;}
    public void setEnableScreenShare(boolean screenShare)
    {
        this.enableScreenShare = screenShare;
    }
    public boolean getIsScreenShareEnabled()
    {
        return this.enableScreenShare;
    }
    private MediaProjection mediaProjection;
    public MediaProjection getMediaProjection() {
        return this.mediaProjection;
    }
    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }
    static 
    {
        Log.setLogLevel(LogLevel.Debug);
        Log.setProvider(new fm.liveswitch.android.LogProvider(LogLevel.Debug));
        License.getCurrent();
    }

    private App(Context context) 
    {
        this.context = context.getApplicationContext();

        handler = new Handler(context.getMainLooper());

        audioOnly = false;
        receiveOnly = false;
        contextMenuItemFlag = new HashMap<>();
        remoteMediaMaps = new HashMap<>();
        sfuDownstreamConnections = new HashMap<>();
        peerConnections = new HashMap<>();
    }

    private static App app;

    public static synchronized App getInstance(Context context) 
    {
        if (app == null) 
        {
            app = new App(context);
        }
        return app;
    }

    private void addRemoteViewOnUiThread(final RemoteMedia remoteMedia) 
    {
        if (layoutManager == null)
            return;

        Runnable r = new Runnable() 
        {
            @Override
            public void run() 
            {
                if (remoteMedia.getView() != null) 
                {
                    remoteMedia.getView().setContentDescription("remoteView_" + remoteMedia.getId());
                }
                layoutManager.addRemoteView(remoteMedia.getId(), remoteMedia.getView());
            }
        };

        handler.post(r);
    }

    private void removeRemoteViewOnUiThread(final RemoteMedia remoteMedia) 
    {
        if (layoutManager == null)
            return;
        clearContextMenuItemFlag(remoteMedia.getId());
        Runnable r = new Runnable() 
        {
            @Override
            public void run() 
            {

                layoutManager.removeRemoteView(remoteMedia.getId());
                remoteMedia.destroy();
            }
        };

        handler.post(r);
    }

    private void layoutOnUiThread() 
    {
        if (layoutManager == null)
            return;

        Runnable r = new Runnable()
        {
            @Override
            public void run() 
            {
                layoutManager.layout();
            }
        };

        handler.post(r);
    }

    public Future<fm.liveswitch.LocalMedia> startLocalMedia(final VideoChatFragment fragment) 
    {
        final Promise<fm.liveswitch.LocalMedia> promise = new Promise<>();

        enableH264 = fm.liveswitch.openh264.Utility.isSupported();
        if(enableH264)
        {
            final String downloadPath = context.getFilesDir().getPath();
            fm.liveswitch.openh264.Utility.downloadOpenH264(downloadPath).waitForResult();

            System.load(fm.liveswitch.PathUtility.combinePaths(downloadPath, fm.liveswitch.openh264.Utility.getLoadLibraryName()));
        }

        // Set up the layout manager.
        fragment.getActivity().runOnUiThread(new Runnable() 
        {
            @Override
            public void run() 
            {
                layoutManager = new LayoutManager(fragment.container);

                if (receiveOnly) 
                {
                    promise.resolve(null);
                }
                else 
                {
                    // Create an echo cancellation context.
                    aecContext = new AecContext();

                    // Set up the local media.
                    if (enableScreenShare) {
                        localMedia = new ScreenShareLocalMedia(mediaProjection, context, enableH264, false, audioOnly, aecContext, enableSimulcast);
                    } else {
                        localMedia = new CameraLocalMedia(context, enableH264, false, audioOnly, aecContext, enableSimulcast);
                    }

                    final View localView = (View)localMedia.getView();
                    if (localView != null)
                    {
                        localView.setContentDescription("localView");
                        videoChatFragmentLayout.registerLocalContextMenu(localView, localMedia.getVideoEncodings());

                    }
                    layoutManager.setLocalView(localView);

                    // Start the local media.
                    localMedia.start().then(new IAction1<fm.liveswitch.LocalMedia>() 
                    {
                        @Override
                        public void invoke(fm.liveswitch.LocalMedia localMedia) 
                        {
                            promise.resolve(null);
                        }
                    },
                    new IAction1<Exception>() 
                    {
                        @Override
                        public void invoke(Exception e) 
                        {
                            promise.reject(e);
                        }
                    });
                }
            }
        });

        return promise;
    }

    public Future<fm.liveswitch.LocalMedia> stopLocalMedia() 
    {
        final Promise<fm.liveswitch.LocalMedia> promise = new Promise();
        videoChatFragmentLayout = null;
        clearContextMenuItemFlag("localView");
        if (localMedia == null)
        {
            promise.resolve(null);
        }
        else
        {
            localMedia.stop().then(new IAction1<fm.liveswitch.LocalMedia>() 
            {
                @Override
                public void invoke(fm.liveswitch.LocalMedia o) 
                {

                    // Tear down the layout manager.
                    if (layoutManager != null) 
                    {
                        layoutManager.removeRemoteViews();
                        layoutManager.unsetLocalView();
                        layoutManager = null;
                    }

                    // Tear down the local media.
                    if (localMedia != null) 
                    {
                        localMedia.destroy(); // localMedia.destroy() will also destroy aecContext.
                        localMedia = null;
                    }
                    promise.resolve(null);
                }
            }, new IAction1<Exception>() 
            {
                @Override
                public void invoke(Exception e) 
                {
                    promise.reject(e);
                }
            });
        }

        return promise;
    }

    // Generate a register token.
    // WARNING: do NOT do this here!
    // Tokens should be generated by a secure server that
    // has authenticated your user identity and is authorized
    // to allow you to register with the LiveSwitch server.
    private String generateToken(ChannelClaim[] claims) 
    {
        return Token.generateClientRegisterToken(applicationId, client.getUserId(), client.getDeviceId(), client.getId(), null, claims, "--replaceThisWithYourOwnSharedSecret--");
    }

    public Future<Channel[]> joinAsync(TextChatFragment textChat) 
    {
        textListener = textChat;
        unRegistering = false;

        // Create a client to manage the channel.
        client = new fm.liveswitch.Client(gatewayUrl, applicationId, userId, deviceId);
        final ChannelClaim[] claims = new ChannelClaim[]{new ChannelClaim(channelId)};

        client.setTag(Integer.toString(mode.getValue()));
        client.setUserAlias(userName);

        client.addOnStateChange(new IAction1<Client>() 
        {
            @Override
            public void invoke(Client client) {
                if (client.getState() == ClientState.Registering)
                {
                    Log.debug("client is registering");
                }
                else if (client.getState() == ClientState.Registered)
                {
                    Log.debug("client is registered");
                }
                else if (client.getState() == ClientState.Unregistering)
                {
                    Log.debug("client is unregistering");
                }
                else if (client.getState() == ClientState.Unregistered)
                {
                    Log.debug("client is unregistered");

                    // Client has failed for some reason:
                    // We do not need to `c.closeAll()` as the client handled this for us as part of unregistering.
                    if (!unRegistering)
                    {

                        // Back off our reregister attempts as they continue to fail to avoid runaway process.
                        ManagedThread.sleep(reRegisterBackoff);
                        if (reRegisterBackoff < maxRegisterBackoff) 
                        {
                            reRegisterBackoff += reRegisterBackoff;
                        }

                        // ReRegister
                        client.register(generateToken(claims)).then(new fm.liveswitch.IAction1<Channel[]>() 
                        {
                            public void invoke(fm.liveswitch.Channel[] channels) 
                            {
                                reRegisterBackoff = 200; // reset for next time
                                onClientRegistered(channels);
                            }
                        }, new IAction1<Exception>() 
                        {
                            @Override
                            public void invoke(Exception e) {
                                Log.error("Failed to reregister with Gateway.", e);
                            }
                        });
                    }
                }
            }
        });

        return client.register(generateToken(claims)).then(new fm.liveswitch.IAction1<Channel[]>() 
        {
            public void invoke(fm.liveswitch.Channel[] channels) 
            {
                onClientRegistered(channels);
            }
        }, new IAction1<Exception>() 
        {
            @Override
            public void invoke(Exception e) 
            {
                Log.error("Failed to register with Gateway.", e);
            }
        });
    }

    private void onClientRegistered(Channel[] channels) 
    {
        channel = channels[0];

        // Monitor the channel remote client changes.
        channel.addOnRemoteClientJoin(new fm.liveswitch.IAction1<fm.liveswitch.ClientInfo>() 
        {
            public void invoke(fm.liveswitch.ClientInfo remoteClientInfo) 
            {
                fm.liveswitch.Log.info("Remote client joined the channel (client ID: " + remoteClientInfo.getId() + ", device ID: " + remoteClientInfo.getDeviceId() + ", user ID: " + remoteClientInfo.getUserId() + ", tag: " + remoteClientInfo.getTag() + ").");

                String n = remoteClientInfo.getUserAlias() != null ? remoteClientInfo.getUserAlias() : remoteClientInfo.getUserId();
                textListener.onPeerJoined(n);
            }
        });
        channel.addOnRemoteClientLeave(new fm.liveswitch.IAction1<fm.liveswitch.ClientInfo>() 
        {
            public void invoke(fm.liveswitch.ClientInfo remoteClientInfo) 
            {
                String n = remoteClientInfo.getUserAlias() != null ? remoteClientInfo.getUserAlias() : remoteClientInfo.getUserId();
                textListener.onPeerLeft(n);
                fm.liveswitch.Log.info("Remote client left the channel (client ID: " + remoteClientInfo.getId() + ", device ID: " + remoteClientInfo.getDeviceId() + ", user ID: " + remoteClientInfo.getUserId() + ", tag: " + remoteClientInfo.getTag() + ").");
            }
        });

        // Monitor the channel remote upstream connection changes.
        channel.addOnRemoteUpstreamConnectionOpen(new fm.liveswitch.IAction1<fm.liveswitch.ConnectionInfo>() 
        {
            public void invoke(fm.liveswitch.ConnectionInfo remoteConnectionInfo) 
            {
                fm.liveswitch.Log.info("Remote client opened upstream connection (connection ID: " + remoteConnectionInfo.getId() + ", client ID: " + remoteConnectionInfo.getClientId() + ", device ID: " + remoteConnectionInfo.getDeviceId() + ", user ID: " + remoteConnectionInfo.getUserId() + ", tag: " + remoteConnectionInfo.getTag() + ").");
                if (mode.equals(Modes.Sfu)) 
                {
                    // Open downstream connection to receive the new upstream connection.
                    openSfuDownstreamConnection(remoteConnectionInfo, null);
                }
            }
        });
        channel.addOnRemoteUpstreamConnectionClose(new fm.liveswitch.IAction1<fm.liveswitch.ConnectionInfo>() 
        {
            public void invoke(fm.liveswitch.ConnectionInfo remoteConnectionInfo) 
            {
                fm.liveswitch.Log.info("Remote client closed upstream connection (connection ID: " + remoteConnectionInfo.getId() + ", client ID: " + remoteConnectionInfo.getClientId() + ", device ID: " + remoteConnectionInfo.getDeviceId() + ", user ID: " + remoteConnectionInfo.getUserId() + ", tag: " + remoteConnectionInfo.getTag() + ").");
            }
        });

        // Monitor the channel peer connection offers.
        channel.addOnPeerConnectionOffer(new fm.liveswitch.IAction1<fm.liveswitch.PeerConnectionOffer>() 
        {
            public void invoke(fm.liveswitch.PeerConnectionOffer peerConnectionOffer) 
            {
                // Accept the peer connection offer.
                openPeerAnswerConnection(peerConnectionOffer, null);
            }
        });

        channel.addOnMessage(new fm.liveswitch.IAction2<fm.liveswitch.ClientInfo, String>() 
        {
            public void invoke(fm.liveswitch.ClientInfo clientInfo, String message) 
            {

                String n = clientInfo.getUserAlias() != null ? clientInfo.getUserAlias() : clientInfo.getUserId();
                textListener.onReceivedText(n, message);
            }
        });

        if (mode.equals(Modes.Mcu)) 
        {

            // Monitor the channel video layout changes.
            channel.addOnMcuVideoLayout(new fm.liveswitch.IAction1<VideoLayout>() 
            {
                    public void invoke(VideoLayout vidLayout) {
                        if (!receiveOnly)
                        {
                            videoLayout = vidLayout;
                            // Force a layout in case the local video preview needs to move.
                            layoutOnUiThread();
                        }
                }
            });

            // Open an MCU connection.
            openMcuConnection(null);
        } 
        else if (mode.equals(Modes.Sfu)) 
        {
            if (!receiveOnly) 
            {
                // Open an upstream SFU connection.
                openSfuUpstreamConnection(null);
            }

            // Open a downstream SFU connection for each remote upstream connection.
            for (ConnectionInfo connectionInfo : channel.getRemoteUpstreamConnectionInfos()) 
            {
                openSfuDownstreamConnection(connectionInfo, null);
            }
        } 
        else if (mode.equals(Modes.Peer)) 
        {
            // Open a peer connection for each remote client.
            for (ClientInfo clientInfo : channel.getRemoteClientInfos()) 
            {
                openPeerOfferConnection(clientInfo, null);
            }
        }
        textListener.onClientRegistered();
    }

    public Future<Object> leaveAsync() 
    {
		if(this.client != null)
        {
            unRegistering = true;

            // Unregister with the server.
            return this.client.unregister().then(new IAction1<Object>(){
                @Override
                public void invoke(Object o) {
                    textListener.onClientUnregistered();
                    dataChannelConnected = false;
                }
            }).fail(new IAction1<Exception>()
            {
                @Override
                public void invoke(Exception e) {
                    Log.debug("Failed to Unregister Client", e);
                }
            });
        }
        else
        {
            return null;
        }
    }

    private McuConnection openMcuConnection(final String tag) 
    {
        // Create remote media to manage incoming media.
        final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, false, audioOnly, aecContext);
        mcuViewId = remoteMedia.getId();

        // Add the remote video view to the layout.
        addRemoteViewOnUiThread(remoteMedia);

        McuConnection connection;

        final DataChannel dataChannel = prepareDataChannel();
        DataStream dataStream = new DataStream(dataChannel);
        
        synchronized(dataChannelLock)
        {
            dataChannels.add(dataChannel);
        }

        AudioStream audioStream = new AudioStream(localMedia, remoteMedia);
        if (receiveOnly)
        {
            audioStream.setLocalDirection(StreamDirection.ReceiveOnly);
        }
        if (audioOnly)
        {
            connection = channel.createMcuConnection(audioStream, dataStream);
        }
        else
        {
            VideoStream videoStream = new VideoStream(localMedia, remoteMedia);
            if (receiveOnly)
            {
                videoStream.setLocalDirection(StreamDirection.ReceiveOnly);
            } else if (enableSimulcast && !audioOnly) {

                videoStream.setSimulcastMode(SimulcastMode.RtpStreamId);
            }
            connection = channel.createMcuConnection(audioStream, videoStream, dataStream);
            EncodingInfo[] remoteEncodings = connection.getInfo().getVideoStream().getSendEncodings();
            if (remoteEncodings != null && remoteEncodings.length > 0) {
                videoStream.setRemoteEncoding(remoteEncodings[0]);
            }
        }

        mcuConnection = connection;

        // Tag the connection (optional).
        if (tag != null) 
        {
            connection.setTag(tag);
        }

        /*
        Embedded TURN servers are used by default.  For more information refer to:
        https://help.frozenmountain.com/docs/liveswitch/server/advanced-topics#TURNintheMediaServer
        */

        // Monitor the connection state changes.
        connection.addOnStateChange(new IAction1<ManagedConnection>() 
        {
            public void invoke(ManagedConnection connection)
            {
                Log.info(connection.getId() + ": MCU connection state is " + connection.getState().toString() + ".");

                // Cleanup if the connection closes or fails.
                if (connection.getState() == ConnectionState.Closing || connection.getState() == ConnectionState.Failing) 
                {
                    if (connection.getRemoteClosed()) 
                    {
                        Log.info(connection.getId() + ": Media server closed the connection.");
                    }
                    removeRemoteViewOnUiThread(remoteMedia);
                    
                    synchronized(dataChannelLock)
                    {
                        dataChannels.remove(dataChannel);
                    }
                    
                    logConnectionState(connection, "MCU");
                    mcuConnection = null;
                }
                else if (connection.getState() == ConnectionState.Failed)
                {
                    // Note: no need to close the connection as it's done for us.
                    openMcuConnection(tag);
                    logConnectionState(connection, "MCU");
                }
                else if (connection.getState() == ConnectionState.Connected)
                {
                    logConnectionState(connection, "MCU");
                }
            }
        });

        // Float the local preview over the mixed video feed for an improved user experience.
        layoutManager.addOnLayout(new fm.liveswitch.IAction1<Layout>() 
        {
            public void invoke(Layout layout)
            {
                if (mcuConnection != null && !receiveOnly && !audioOnly)
                {
                    fm.liveswitch.LayoutUtility.floatLocalPreview(layout, videoLayout, mcuConnection.getId(), mcuViewId, localMedia.getViewSink());
                }
            }
        });

        // Open the connection.
        connection.open();

        return connection;
    }

    private fm.liveswitch.SfuUpstreamConnection openSfuUpstreamConnection(final String tag) 
    {
        // Create the connection.
        fm.liveswitch.SfuUpstreamConnection connection;

        final DataChannel dataChannel = prepareDataChannel();
        DataStream dataStream = new DataStream(dataChannel);
        
        synchronized(dataChannelLock)
        {
            dataChannels.add(dataChannel);
        }
                
        VideoStream videoStream = null;
        AudioStream audioStream = null;

        if (localMedia.getAudioTrack() != null)
        {
            audioStream = new AudioStream(localMedia);
        }

        if (localMedia.getVideoTrack() != null)
        {
            videoStream = new VideoStream((localMedia));
            if (enableSimulcast) {
                videoStream.setSimulcastMode(SimulcastMode.RtpStreamId);
            }
        }
        connection = channel.createSfuUpstreamConnection(audioStream, videoStream, dataStream);

        sfuUpstreamConnection = connection;

        // Tag the connection (optional).
        if (tag != null) 
        {
            connection.setTag(tag);
        }

        /*
        Embedded TURN servers are used by default.  For more information refer to:
        https://help.frozenmountain.com/docs/liveswitch/server/advanced-topics#TURNintheMediaServer
        */

        // Monitor the connection state changes.
        connection.addOnStateChange(new IAction1<ManagedConnection>() 
        {
            public void invoke(ManagedConnection connection)
            {
                Log.info(connection.getId() + ": SFU upstream connection state is " + connection.getState().toString() + ".");

                // Cleanup if the connection closes or fails.
                if (connection.getState() == ConnectionState.Closing || connection.getState() == ConnectionState.Failing) 
                {
                    if (connection.getRemoteClosed()) 
                    {
                        Log.info(connection.getId() + ": Media server closed the connection.");
                    }
                    sfuUpstreamConnection = null;
                    
                    synchronized(dataChannelLock)
                    {
                        dataChannels.remove(dataChannel);
                    }
                    
                    logConnectionState(connection, "SFU Upstream");
                }
                else if (connection.getState() == ConnectionState.Failed)
                {
                    // Note: no need to close the connection as it's done for us.
                    openSfuUpstreamConnection(tag);
                    logConnectionState(connection, "SFU Upstream");
                }
                else if (connection.getState() == ConnectionState.Connected)
                {
                    logConnectionState(connection, "SFU Upstream");
                }
            }
        });

        // Open the connection.
        connection.open();

        return connection;
    }

    private fm.liveswitch.SfuDownstreamConnection openSfuDownstreamConnection(final ConnectionInfo remoteConnectionInfo, final String tag) 
    {
        // Create remote media to manage incoming media.
        final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, false, audioOnly, aecContext);

        // Add the remote video view to the layout.
        addRemoteViewOnUiThread(remoteMedia);
        final View remoteView = remoteMedia.getView();
        if (remoteView != null) {
            remoteView.setContentDescription("remoteView_" + remoteMedia.getId());
            videoChatFragmentLayout.registerRemoteContextMenu(remoteView, remoteConnectionInfo.getHasVideo() ? remoteConnectionInfo.getVideoStream().getSendEncodings() : null);
        }

        SfuDownstreamConnection connection;

        DataChannel dataChannel = null;
        DataStream dataStream = null;
        if (remoteConnectionInfo.getHasData())
        {
            dataChannel = prepareDataChannel();
            dataStream = new DataStream(dataChannel);
        }

        VideoStream videoStream = null;
        AudioStream audioStream = null;
        if (remoteConnectionInfo.getHasAudio())
        {
            audioStream = new AudioStream(null, remoteMedia);
        }
        if (remoteConnectionInfo.getHasVideo() && !audioOnly )
        {
            videoStream = new VideoStream(null, remoteMedia);

            if (enableSimulcast) {
                EncodingInfo[] remoteEncodings = remoteConnectionInfo.getVideoStream().getSendEncodings();
                if (remoteEncodings != null && remoteEncodings.length > 0) {
                    videoStream.setRemoteEncoding(remoteEncodings[0]);
                }
            }
        }

        connection = channel.createSfuDownstreamConnection(remoteConnectionInfo, audioStream, videoStream, dataStream);

        sfuDownstreamConnections.put(remoteMedia.getId(), connection);
        remoteMediaMaps.put(remoteMedia.getId(), connection);

        // Tag the connection (optional).
        if (tag != null) 
        {
            connection.setTag(tag);
        }

        /*
        Embedded TURN servers are used by default.  For more information refer to:
        https://help.frozenmountain.com/docs/liveswitch/server/advanced-topics#TURNintheMediaServer
        */

        // Monitor the connection state changes.
        connection.addOnStateChange(new IAction1<ManagedConnection>() 
        {
            public void invoke(ManagedConnection connection)
            {
                Log.info(connection.getId() + ": SFU downstream connection state is " + connection.getState().toString() + ".");

                // Cleanup if the connection closes or fails.
                if (connection.getState() == ConnectionState.Closing || connection.getState() == ConnectionState.Failing) 
                {
                    if (connection.getRemoteClosed()) 
                    {
                        Log.info(connection.getId() + ": Media server closed the connection.");
                    }
                    removeRemoteViewOnUiThread(remoteMedia);
                    sfuDownstreamConnections.remove(remoteMedia.getId());
                    remoteMediaMaps.remove(remoteMedia.getId());
                    logConnectionState(connection, "SFU Downstream");
                }
                else if (connection.getState() == ConnectionState.Failed)
                {
                    // Note: no need to close the connection as it's done for us.
                    openSfuDownstreamConnection(remoteConnectionInfo, tag);
                    logConnectionState(connection, "SFU Downstream");
                }
                else if (connection.getState() == ConnectionState.Connected)
                {
                    logConnectionState(connection, "SFU Downstream");
                }
            }
        });

        // Open the connection.
        connection.open();

        return connection;
    }

    public fm.liveswitch.PeerConnection openPeerOfferConnection(final ClientInfo remoteClientInfo, final String tag) 
    {
        // Create remote media to manage incoming media.
        final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, false, audioOnly, aecContext);

        // Add the remote video view to the layout.
        addRemoteViewOnUiThread(remoteMedia);
        final View remoteView = remoteMedia.getView();
        if (remoteView != null) {
            remoteView.setContentDescription("remoteView_" + remoteMedia.getId());
            videoChatFragmentLayout.registerRemoteContextMenu(remoteView, null);
        }
        final PeerConnection connection;
        AudioStream audioStream = new AudioStream(localMedia, remoteMedia);
        VideoStream videoStream = null;
        if (!audioOnly)
        {
            videoStream = new VideoStream(localMedia, remoteMedia);
        }

        //Please note that DataStreams can also be added to Peer-to-peer connections.
        //Nevertheless, since peer connections do not connect to the media server, there may arise
        //incompatibilities with the peers that do not support DataStream (e.g. Microsoft Edge browser:
        //https://developer.microsoft.com/en-us/microsoft-edge/platform/status/rtcdatachannels/?filter=f3f0000bf&search=rtc&q=data%20channels).
        //For a solution around this issue and complete documentation visit:
        //https://help.frozenmountain.com/docs/liveswitch1/working-with-datachannels

        connection = channel.createPeerConnection(remoteClientInfo, audioStream, videoStream);

        peerConnections.put(connection.getId(), connection);
        remoteMediaMaps.put(remoteMedia.getId(), connection);

        // Tag the connection (optional).
        if (tag != null) 
        {
            connection.setTag(tag);
        }

        /*
        Embedded TURN servers are used by default.  For more information refer to:
        https://help.frozenmountain.com/docs/liveswitch/server/advanced-topics#TURNintheMediaServer
        */

        // Monitor the connection state changes.
        connection.addOnStateChange(new IAction1<ManagedConnection>() 
        {
            public void invoke(ManagedConnection connection)
            {
                Log.info(connection.getId() + ": Peer connection state is " + connection.getState().toString() + ".");

                // Cleanup if the connection closes or fails.
                if (connection.getState() == ConnectionState.Closing || connection.getState() == ConnectionState.Failing) 
                {
                    if (connection.getRemoteRejected()) 
                    {
                        Log.info(connection.getId() + ": Remote peer rejected the offer.");
                    } else if (connection.getRemoteClosed()) 
                    {
                        Log.info(connection.getId() + ": Remote peer closed the connection.");
                    }
                    removeRemoteViewOnUiThread(remoteMedia);
                    peerConnections.remove(connection.getId());
                    remoteMediaMaps.remove(remoteMedia.getId());
                    logConnectionState(connection, "Peer");
                }
                else if (connection.getState() == ConnectionState.Failed)
                {
                    // Note: no need to close the connection as it's done for us.
                    openPeerOfferConnection(remoteClientInfo, tag);
                    logConnectionState(connection, "Peer");
                }
                else if (connection.getState() == ConnectionState.Connected)
                {
                    logConnectionState(connection, "Peer");
                }
            }
        });

        // Open the connection (sends an offer to the remote peer).
        connection.open();

        return connection;
    }

    private fm.liveswitch.PeerConnection openPeerAnswerConnection(final fm.liveswitch.PeerConnectionOffer peerConnectionOffer, final String tag)
    {
        // Create remote media to manage incoming media.

        boolean disableRemoteVideo = audioOnly;
        if (peerConnectionOffer.getHasVideo())
        {
            /*
            The remote client is offering audio AND video => they are expecting a VideoStream in the connection.
            To create this connection successfully we must include a VideoStream, even though we may have chosen to be in audio only mode.
            In this case we simply set the VideoStream's direction to inactive.
            */
            disableRemoteVideo = false;
        }
        final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, false, disableRemoteVideo, aecContext);

        // Add the remote video view to the layout.
        addRemoteViewOnUiThread(remoteMedia);
        final View remoteView = remoteMedia.getView();
        if (remoteView != null) {
            remoteView.setContentDescription("remoteView_" + remoteMedia.getId());
            videoChatFragmentLayout.registerRemoteContextMenu(remoteView, null);
        }

        final PeerConnection connection;

        VideoStream videoStream = null;
        AudioStream audioStream = null;
        if (peerConnectionOffer.getHasAudio())
        {
            audioStream = new AudioStream(localMedia, remoteMedia);
        }

        if (peerConnectionOffer.getHasVideo())
        {
            videoStream = new VideoStream(localMedia, remoteMedia);
            if (audioOnly)
            {
                videoStream.setLocalDirection(StreamDirection.Inactive);
            }
        }

        //Please note that DataStreams can also be added to Peer-to-peer connections.
        //Nevertheless, since peer connections do not connect to the media server, there may arise
        //incompatibilities with the peers that do not support DataStream (e.g. Microsoft Edge browser:
        //https://developer.microsoft.com/en-us/microsoft-edge/platform/status/rtcdatachannels/?filter=f3f0000bf&search=rtc&q=data%20channels).
        //For a solution around this issue and complete documentation visit:
        //https://help.frozenmountain.com/docs/liveswitch1/working-with-datachannels

        connection = channel.createPeerConnection(peerConnectionOffer, audioStream, videoStream);
        peerConnections.put(connection.getId(), connection);
        remoteMediaMaps.put(remoteMedia.getId(), connection);

        // Tag the connection (optional).
        if (tag != null) {
            connection.setTag(tag);
        }

        /*
        Embedded TURN servers are used by default.  For more information refer to:
        https://help.frozenmountain.com/docs/liveswitch/server/advanced-topics#TURNintheMediaServer
        */

        // Monitor the connection state changes.
        connection.addOnStateChange(new IAction1<ManagedConnection>() 
        {
            public void invoke(ManagedConnection connection)
            {
                Log.info(connection.getId() + ": Peer connection state is " + connection.getState().toString() + ".");

                // Cleanup if the connection closes or fails.
                if (connection.getState() == ConnectionState.Closing || connection.getState() == ConnectionState.Failing) 
                {
                    if (connection.getRemoteClosed()) {
                        Log.info(connection.getId() + ": Remote peer closed the connection.");
                    }
                    removeRemoteViewOnUiThread(remoteMedia);
                    peerConnections.remove(connection.getId());
                    remoteMediaMaps.remove(remoteMedia.getId());
                    logConnectionState(connection, "Peer");
                }
                else if (connection.getState() == ConnectionState.Failed)
                {
                    // Note: no need to close the connection as it's done for us.
                    // Note: do not offer a new answer here. Let the offerer reoffer and then we answer normally.
                    logConnectionState(connection, "Peer");
                }
                else if (connection.getState() == ConnectionState.Connected)
                {
                    logConnectionState(connection, "Peer");
                }
            }
        });

        // Open the connection (sends an answer to the remote peer).
        connection.open();

        return connection;
    }

    public void useNextVideoDevice() 
    {
        if (localMedia != null && localMedia.getVideoSource() != null) 
        {
            localMedia.changeVideoSourceInput(usingFrontVideoDevice ?
                    ((Camera2Source) localMedia.getVideoSource()).getBackInput() :
                    ((Camera2Source) localMedia.getVideoSource()).getFrontInput());

            usingFrontVideoDevice = !usingFrontVideoDevice;
        }
    }

    public Future<Object> pauseLocalVideo() 
    {
        if (!enableScreenShare && localMedia != null)
        {
            VideoSource videoSource = localMedia.getVideoSource();
            if (videoSource != null)
            {
                if (videoSource.getState() == MediaSourceState.Started)
                {
                    return videoSource.stop();
                }
            }
        }
        return Promise.resolveNow();
    }

    public Future<Object> resumeLocalVideo() 
    {
        if (localMedia != null)
        {
            VideoSource videoSource = localMedia.getVideoSource();
            if (videoSource != null)
            {
                if (videoSource.getState() == MediaSourceState.Stopped)
                {
                    return videoSource.start();
                }
            }
        }
        return Promise.resolveNow();
    }

    public void writeLine(String message)
    {
        if (channel != null) // If the registration has not happened then "channel" will be null. So we want to send messages only after registration.
        {
            channel.sendMessage(message);
        }
    }

    private void logConnectionState(ManagedConnection conn, String connectionType)
    {
        String streams = "";
        int streamCount = 0;
        if (conn.getAudioStream() != null)
        {
            streamCount++;
            streams = "audio";
        }
        if (conn.getDataStream() != null)
        {
            if (streams.length() > 0)
            {
                streams += "/";
            }
            streamCount++;
            streams += "data";
        }
        if (conn.getVideoStream() != null)
        {
            if (streams.length() > 0)
            {
                streams += "/";
            }
            streamCount++;
            streams += "video";
        }
        if (streamCount > 1)
        {
            streams += " streams.";
        }
        else
        {
            streams += " stream.";
        }

        if (conn.getState() == ConnectionState.Connected)
        {
            textListener.onReceivedText("System", connectionType + " connection connected with " + streams);
        }
        else if (conn.getState() == ConnectionState.Closing)
        {
            textListener.onReceivedText("System", connectionType + " connection closing for " + streams);
        }
        else if (conn.getState() == ConnectionState.Failing)
        {
            String eventString = connectionType + " connection failing for " + streams;
            if (conn.getError() != null)
            {
                eventString += conn.getError().getDescription();
            }
            textListener.onReceivedText("System", eventString);
        }
        else if (conn.getState() == ConnectionState.Closed)
        {
            textListener.onReceivedText("System", connectionType + " connection closed for " + streams);
        }
        else if (conn.getState() == ConnectionState.Failed)
        {
            textListener.onReceivedText("System", connectionType + " connection failed for " + streams);
        }
    }


    private IAction0 sendMessageInDataChannels()
    {
        return new IAction0()
        {
            @Override
            public void invoke()
            {
                DataChannel[] channels;
                synchronized(dataChannelLock)
                {
                    channels = dataChannels.toArray(new DataChannel[dataChannels.size()]);
                }
                for (DataChannel channel: channels)
                {
                    channel.sendDataString("Hello world!");
                }
            }
        };
    }

    private DataChannel prepareDataChannel()
    {
        DataChannel dataChannel = new DataChannel("data");
        dataChannel.setOnReceive(new IAction1<DataChannelReceiveArgs>()
        {
            @Override
            public void invoke(DataChannelReceiveArgs dataChannelReceiveArgs)
            {
                if (!dataChannelConnected)
                {
                    if (dataChannelReceiveArgs.getDataString() != null)
                    {
                        textListener.onReceivedText("System", "Data channel connection established. Received test message fromserver: " + dataChannelReceiveArgs.getDataString());
                    }
                    dataChannelConnected = true;
                }
            }
        });
        dataChannel.addOnStateChange(new IAction1<DataChannel>()
        {
            @Override
            public void invoke(DataChannel dataChannel)
            {
                if (dataChannel.getState() == DataChannelState.Connected)
                {
                    if (dataChannelsMessageTimer == null)
                    {
                        dataChannelsMessageTimer = new ManagedTimer(1000, sendMessageInDataChannels() );
                        dataChannelsMessageTimer.start();
                    }
                }
            }
        });
        return dataChannel;
    }

    public void changeSendEncodings(int index) {
        VideoEncodingConfig[] encodings = localMedia.getVideoEncodings();
        encodings[index].setDeactivated(!encodings[index].getDeactivated());
        this.localMedia.setVideoEncodings(encodings);
    }

    public void changeReceiveEncodings(String id, int index) {
            SfuDownstreamConnection connection = sfuDownstreamConnections.get(id.replace("remoteView_", "").trim());
            EncodingInfo[] encodings = connection.getRemoteConnectionInfo().getVideoStream().getSendEncodings();
            if (encodings != null && encodings.length > 1) {
                ConnectionConfig config = connection.getConfig();
                config.setRemoteVideoEncoding(encodings[index]);
                connection.update(config).then(new fm.liveswitch.IAction1<Object>() {
                    @Override
                    public void invoke(Object o) {
                        Log.debug("Updated video encoding to: " + encodings[index] + " for connection: " + connection);
                    }
                }).fail((ex) -> {
                    Log.error("Could not change video stream encoding for connection: " + connection, ex);
                });
            }
    }

    public void toggleMuteAudio() {
        ConnectionConfig config = null;
        if (sfuUpstreamConnection != null) {
            config = sfuUpstreamConnection.getConfig();
            config.setLocalAudioMuted(!config.getLocalAudioMuted());
            sfuUpstreamConnection.update(config);
        }
        if (mcuConnection != null) {
            config = mcuConnection.getConfig();
            config.setLocalAudioMuted(!config.getLocalAudioMuted());
            mcuConnection.update(config);
        }
        for (PeerConnection peerConnection: peerConnections.values()) {
            config = peerConnection.getConfig();
            config.setLocalAudioMuted(!config.getLocalAudioMuted());
            peerConnection.update(config);
        }
        if (config != null) {
            contextMenuItemFlag.put("MuteAudio", config.getLocalAudioMuted());
        }
    }

    public void toggleMuteVideo() {
        ConnectionConfig config = null;
        if (sfuUpstreamConnection != null) {
            config = sfuUpstreamConnection.getConfig();
            config.setLocalVideoMuted(!config.getLocalVideoMuted());
            sfuUpstreamConnection.update(config);
        }
        if (mcuConnection != null) {
            config = mcuConnection.getConfig();
            config.setLocalVideoMuted(!config.getLocalVideoMuted());
            mcuConnection.update(config);
        }
        for (PeerConnection peerConnection: peerConnections.values()) {
            config = peerConnection.getConfig();
            config.setLocalVideoMuted(!config.getLocalVideoMuted());
            peerConnection.update(config);
        }
        if (config != null) {
            contextMenuItemFlag.put("MuteVideo", config.getLocalVideoMuted());
        }
    }

    public void toggleLocalDisableAudio() {
        ConnectionConfig config = null;
        if (sfuUpstreamConnection != null) {
            config = sfuUpstreamConnection.getConfig();
            config.setLocalAudioDisabled(!config.getLocalAudioDisabled());
            sfuUpstreamConnection.update(config);
        }
        if (mcuConnection != null) {
            config = mcuConnection.getConfig();
            config.setLocalAudioDisabled(!config.getLocalAudioDisabled());
            mcuConnection.update(config);
        }
        for (PeerConnection peerConnection : peerConnections.values()) {
            config = peerConnection.getConfig();
            config.setLocalAudioDisabled(!config.getLocalAudioDisabled());
            peerConnection.update(config);
        }
        if (config != null) {
            contextMenuItemFlag.put("DisableAudio", config.getLocalAudioDisabled());
        }
    }

    public void toggleRemoteDisableAudio(String remoteId) {
        String id = remoteId.replace("remoteView_", "");
        ManagedConnection downStream = remoteMediaMaps.get(id);
        ConnectionConfig config = downStream.getConfig();
        config.setRemoteAudioDisabled(!config.getRemoteAudioDisabled());
        contextMenuItemFlag.put(remoteId+"DisableAudio", config.getRemoteAudioDisabled());
        downStream.update(config);
    }

    public void toggleLocalDisableVideo() {
        ConnectionConfig config = null;
        if (sfuUpstreamConnection != null) {
            config = sfuUpstreamConnection.getConfig();
            config.setLocalVideoDisabled(!config.getLocalVideoDisabled());
            sfuUpstreamConnection.update(config);
        }
        if (mcuConnection != null) {
            config = mcuConnection.getConfig();
            config.setLocalVideoDisabled(!config.getLocalVideoDisabled());
            mcuConnection.update(config);
        }
        for (PeerConnection peerConnection : peerConnections.values()) {
            config = peerConnection.getConfig();
            config.setLocalVideoDisabled(!config.getLocalVideoDisabled());

            peerConnection.update(config);
        }
        if (config != null) {
            contextMenuItemFlag.put("DisableVideo", config.getLocalVideoDisabled());
        }
    }

    public void toggleRemoteDisableVideo(String remoteId) {
        String id = remoteId.replace("remoteView_","");
        ManagedConnection downStream = remoteMediaMaps.get(id);
        ConnectionConfig config = downStream.getConfig();
        config.setRemoteVideoDisabled(!config.getRemoteVideoDisabled());
        contextMenuItemFlag.put(remoteId+"DisableVideo", config.getRemoteVideoDisabled());
        downStream.update(config);
    }

    public void clearContextMenuItemFlag(String id) {
        Iterator<String> iterator = contextMenuItemFlag.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (key.contains(id)) {
                iterator.remove();
            }
        }
    }

    public interface OnReceivedTextListener 
    {
        void onReceivedText(String name, String message);
        void onPeerJoined(String name);
        void onPeerLeft(String name);
        void onClientRegistered();
        void onClientUnregistered();
    }
}
