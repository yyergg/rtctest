package com.example.yyergg.webrtctest;

import android.Manifest;
import android.app.Activity;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

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
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

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
    public static final String VIDEO_TRACK_ID = "Chasewind";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "com.example.yyergg.webrtctest.CAPTURETOTEXTURE";
    private static final String[] MANDATORY_PERMISSIONS = {"Manifest.permission.CAMERA"};

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private PeerConnectionFactory factory;
    private PeerConnectionParameters peerConnectionParameters;
    private MediaConstraints pcConstraints;
    private LinkedList<IceCandidate> queuedRemoteCandidates;
    private EglBase rootEglBase;
    private SurfaceViewRenderer localRender;
    private LinearLayout localLayout;
    private PeerConnectionFactory.Options options;
    private VideoCapturer capturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pcConstraints = new MediaConstraints();
        rootEglBase = EglBase.create();

        localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        localRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setZOrderMediaOverlay(true);
        localRender.setEnableHardwareScaler(true);

        localLayout = (LinearLayout) findViewById(R.id.local_video_layout);

        String[] permissions = new String[]{Manifest.permission.CAMERA};

        ActivityCompat.requestPermissions(this, permissions, 0);



        options = null;
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;
        if (options != null) {
            Log.d(S_TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        if (!PeerConnectionFactory.initializeAndroidGlobals(
                this, true, true, true)) {
            Log.d(S_TAG,"Failed to initializeAndroidGlobals");
        }
        factory = new PeerConnectionFactory(options);

        capturer = createCameraCapturer(new Camera2Enumerator(this));
        videoSource = factory.createVideoSource(capturer);
        capturer.startCapture(1280, 720, 30);

        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addRenderer(new VideoRenderer(localRender));

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
