package com.pzin.walkietalkie

import android.app.Application
import android.content.Context
import org.webrtc.*
import org.webrtc.PeerConnection
import timber.log.Timber


class RTCClient(
    context: Application,
    observer: PeerConnection.Observer,
    private var cameraControls: CameraControlsListener,
    private var signallingClient: FirebaseSignallingClient
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }

    private val rootEglBase: EglBase = EglBase.create()

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder(
            listOf("stun:stun1.l.google.com:19302", "stun:stun2.l.google.com:19302")
        ).createIceServer()
    )

    private var isFrontFacingCamera = true

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) =
        peerConnectionFactory.createPeerConnection(
            iceServer,
            observer
        )

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun switchCamera() {
        videoCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(p0: Boolean) {
                isFrontFacingCamera = !isFrontFacingCamera
                cameraControls.onSwitchCamera(shouldMirror = isFrontFacingCamera)
            }

            override fun onCameraSwitchError(p0: String?) {
            }
        })
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)

        (videoCapturer as VideoCapturer).initialize(
            surfaceTextureHelper,
            localVideoOutput.context,
            localVideoSource.capturerObserver
        )

        videoCapturer.startCapture(1920, 1080, 30)

        val localVideoTrack =
            peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)

        localVideoTrack.addSink(localVideoOutput)

        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)

        localStream.addTrack(localVideoTrack)
        peerConnection?.addStream(localStream)
    }

    private fun PeerConnection.call() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        createOffer(object : SdpObserver {
            override fun onSetFailure(p0: String?) {}

            override fun onSetSuccess() {}

            override fun onCreateSuccess(desc: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {}

                    override fun onSetSuccess() {
                        signallingClient.createOffer(desc, MainActivity.ROOM_ID)
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {

                    }

                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }

            override fun onCreateFailure(p0: String?) {}
        }, constraints)
    }

    private fun PeerConnection.answer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        createAnswer(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
            }

            override fun onSetSuccess() {}

            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                        signallingClient.createAnswer(p0, MainActivity.ROOM_ID)
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, p0)
            }

            override fun onCreateFailure(p0: String?) {
                Timber.tag("WEB_RTC").e(p0)
            }
        }, constraints)
    }

    fun call() = peerConnection?.call()

    fun answer() = peerConnection?.answer()

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Timber.tag("WEB_RTC").e("onRemoteSessionReceived onSetFailure " + p0)

            }

            override fun onSetSuccess() {
                Timber.tag("WEB_RTC").e("onRemoteSessionReceived onSetSuccess")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                Timber.tag("WEB_RTC").e("onRemoteSessionReceived onCreateSuccess " + p0)

            }

            override fun onCreateFailure(p0: String?) {
                Timber.tag("WEB_RTC").e("onRemoteSessionReceived onCreateFailure " + p0)

            }
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }
}

interface CameraControlsListener {
    fun onSwitchCamera(shouldMirror: Boolean)
}