package com.example.yyergg.webrtctest;

/**
 * Created by yyergg on 2017/2/2.
 */

class PeerConnectionParameters {
    public final boolean videoCallEnabled;
    public final boolean loopback;
    public final boolean tracing;
    public final int videoWidth;
    public final int videoHeight;
    public final int videoFps;
    public final int videoMaxBitrate;
    public final String videoCodec;
    public final boolean videoCodecHwAcceleration;
    public final boolean videoFlexfecEnabled;
    public final int audioStartBitrate;
    public final String audioCodec;
    public final boolean noAudioProcessing;
    public final boolean aecDump;
    public final boolean useOpenSLES;
    public final boolean disableBuiltInAEC;
    public final boolean disableBuiltInAGC;
    public final boolean disableBuiltInNS;
    public final boolean enableLevelControl;
    private final DataChannelParameters dataChannelParameters;

    public PeerConnectionParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
                                    int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec,
                                    boolean videoCodecHwAcceleration, boolean videoFlexfecEnabled, int audioStartBitrate,
                                    String audioCodec, boolean noAudioProcessing, boolean aecDump, boolean useOpenSLES,
                                    boolean disableBuiltInAEC, boolean disableBuiltInAGC, boolean disableBuiltInNS,
                                    boolean enableLevelControl) {
        this(videoCallEnabled, loopback, tracing, videoWidth, videoHeight, videoFps, videoMaxBitrate,
                videoCodec, videoCodecHwAcceleration, videoFlexfecEnabled, audioStartBitrate, audioCodec,
                noAudioProcessing, aecDump, useOpenSLES, disableBuiltInAEC, disableBuiltInAGC,
                disableBuiltInNS, enableLevelControl, null);
    }

    public PeerConnectionParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
                                    int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec,
                                    boolean videoCodecHwAcceleration, boolean videoFlexfecEnabled, int audioStartBitrate,
                                    String audioCodec, boolean noAudioProcessing, boolean aecDump, boolean useOpenSLES,
                                    boolean disableBuiltInAEC, boolean disableBuiltInAGC, boolean disableBuiltInNS,
                                    boolean enableLevelControl, DataChannelParameters dataChannelParameters) {
        this.videoCallEnabled = videoCallEnabled;
        this.loopback = loopback;
        this.tracing = tracing;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoFps = videoFps;
        this.videoMaxBitrate = videoMaxBitrate;
        this.videoCodec = videoCodec;
        this.videoFlexfecEnabled = videoFlexfecEnabled;
        this.videoCodecHwAcceleration = videoCodecHwAcceleration;
        this.audioStartBitrate = audioStartBitrate;
        this.audioCodec = audioCodec;
        this.noAudioProcessing = noAudioProcessing;
        this.aecDump = aecDump;
        this.useOpenSLES = useOpenSLES;
        this.disableBuiltInAEC = disableBuiltInAEC;
        this.disableBuiltInAGC = disableBuiltInAGC;
        this.disableBuiltInNS = disableBuiltInNS;
        this.enableLevelControl = enableLevelControl;
        this.dataChannelParameters = dataChannelParameters;
    }

    public static class DataChannelParameters {
        public final boolean ordered;
        public final int maxRetransmitTimeMs;
        public final int maxRetransmits;
        public final String protocol;
        public final boolean negotiated;
        public final int id;

        public DataChannelParameters(boolean ordered, int maxRetransmitTimeMs, int maxRetransmits,
                                     String protocol, boolean negotiated, int id) {
            this.ordered = ordered;
            this.maxRetransmitTimeMs = maxRetransmitTimeMs;
            this.maxRetransmits = maxRetransmits;
            this.protocol = protocol;
            this.negotiated = negotiated;
            this.id = id;
        }
    }
}
