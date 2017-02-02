package com.example.yyergg.webrtctest;

import android.app.Activity;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class MainActivity extends AppCompatActivity {
    public static final String S_TAG = "MainActivity";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "com.example.yyergg.webrtctest.CAPTURETOTEXTURE";


    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private PeerConnectionParameters peerConnectionParameters;
    private MediaConstraints pcConstraints;
    private LinkedList<IceCandidate> queuedRemoteCandidates;
    private EglBase rootEglBase;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pcConstraints = new MediaConstraints();
        rootEglBase = EglBase.create();
    }

    private void createPeerConnectionInternal(EglBase.Context renderEGLContext) {
        Log.d(S_TAG, "PCConstraints: " + pcConstraints.toString());
        queuedRemoteCandidates = new LinkedList<IceCandidate>();

        if (videoCallEnabled) {
            Log.d(S_TAG, "EGLContext: " + renderEGLContext);
            factory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
        }

        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);

        if (dataChannelEnabled) {
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = peerConnectionParameters.dataChannelParameters.ordered;
            init.negotiated = peerConnectionParameters.dataChannelParameters.negotiated;
            init.maxRetransmits = peerConnectionParameters.dataChannelParameters.maxRetransmits;
            init.maxRetransmitTimeMs = peerConnectionParameters.dataChannelParameters.maxRetransmitTimeMs;
            init.id = peerConnectionParameters.dataChannelParameters.id;
            init.protocol = peerConnectionParameters.dataChannelParameters.protocol;
            dataChannel = peerConnection.createDataChannel("ApprtcDemo data", init);
        }
        isInitiator = false;

        // Set default WebRTC tracing and INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_DEFAULT));
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

        mediaStream = factory.createLocalMediaStream("ARDAMS");
        if (videoCallEnabled) {
            mediaStream.addTrack(createVideoTrack(videoCapturer));
        }

        mediaStream.addTrack(createAudioTrack());
        peerConnection.addStream(mediaStream);
        if (videoCallEnabled) {
            findVideoSender();
        }

        if (peerConnectionParameters.aecDump) {
            try {
                aecDumpFileDescriptor =
                        ParcelFileDescriptor.open(new File(Environment.getExternalStorageDirectory().getPath()
                                        + File.separator + "Download/audio.aecdump"),
                                ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                                        | ParcelFileDescriptor.MODE_TRUNCATE);
                factory.startAecDump(aecDumpFileDescriptor.getFd(), -1);
            } catch (IOException e) {
                Log.e(TAG, "Can not open aecdump file", e);
            }
        }

        Log.d(TAG, "Peer connection created.");
    }


    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidate(candidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidatesRemoved(candidates);
                }
            });
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(S_TAG, "SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(S_TAG, "IceConnectionState: " + newState);
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        events.onIceConnected();
                    } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected();
                    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                        reportError("ICE connection failed.");
                    }
                }
            });
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(S_TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(S_TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null || isError) {
                        return;
                    }
                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        reportError("Weird-looking stream: " + stream);
                        return;
                    }
                    if (stream.videoTracks.size() == 1) {
                        remoteVideoTrack = stream.videoTracks.get(0);
                        remoteVideoTrack.setEnabled(renderVideo);
                        for (VideoRenderer.Callbacks remoteRender : remoteRenders) {
                            remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
                        }
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    remoteVideoTrack = null;
                }
            });
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            Log.d(S_TAG, "New Data channel " + dc.label());

            if (!dataChannelEnabled)
                return;

            dc.registerObserver(new DataChannel.Observer() {
                public void onBufferedAmountChange(long previousAmount) {
                    Log.d(S_TAG, "Data channel buffered amount changed: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onStateChange() {
                    Log.d(S_TAG, "Data channel state changed: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onMessage(final DataChannel.Buffer buffer) {
                    if (buffer.binary) {
                        Log.d(S_TAG, "Received binary msg over " + dc);
                        return;
                    }
                    ByteBuffer data = buffer.data;
                    final byte[] bytes = new byte[data.capacity()];
                    data.get(bytes);
                    String strData = new String(bytes);
                    Log.d(S_TAG, "Got msg: " + strData + " over " + dc);
                }
            });
        }

        @Override
        public void onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
        }
    }

    /**
     * Peer connection events.
     */
    public interface PeerConnectionEvents {
        /**
         * Callback fired once local SDP is created and set.
         */
        void onLocalDescription(final SessionDescription sdp);

        /**
         * Callback fired once local Ice candidate is generated.
         */
        void onIceCandidate(final IceCandidate candidate);

        /**
         * Callback fired once local ICE candidates are removed.
         */
        void onIceCandidatesRemoved(final IceCandidate[] candidates);

        /**
         * Callback fired once connection is established (IceConnectionState is
         * CONNECTED).
         */
        void onIceConnected();

        /**
         * Callback fired once connection is closed (IceConnectionState is
         * DISCONNECTED).
         */
        void onIceDisconnected();

        /**
         * Callback fired once peer connection is closed.
         */
        void onPeerConnectionClosed();

        /**
         * Callback fired once peer connection statistics is ready.
         */
        void onPeerConnectionStatsReady(final StatsReport[] reports);

        /**
         * Callback fired once peer connection error happened.
         */
        void onPeerConnectionError(final String description);
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer = null;
        Logging.d(S_TAG, "Creating capturer using camera1 API.");
        videoCapturer = createCameraCapturer(new Camera1Enumerator(getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false)));
        if (videoCapturer == null) {
            Log.d(S_TAG,"Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(S_TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(S_TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(S_TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(S_TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    class SignalingParameters {
        public final List<PeerConnection.IceServer> iceServers;
        public final boolean initiator;
        public final String clientId;
        public final String wssUrl;
        public final String wssPostUrl;
        public final SessionDescription offerSdp;
        public final List<IceCandidate> iceCandidates;

        public SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator,
                                   String clientId, String wssUrl, String wssPostUrl, SessionDescription offerSdp,
                                   List<IceCandidate> iceCandidates) {
            this.iceServers = iceServers;
            this.initiator = initiator;
            this.clientId = clientId;
            this.wssUrl = wssUrl;
            this.wssPostUrl = wssPostUrl;
            this.offerSdp = offerSdp;
            this.iceCandidates = iceCandidates;
        }
    }
}
