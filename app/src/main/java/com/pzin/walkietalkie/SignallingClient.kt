package com.pzin.walkietalkie

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import timber.log.Timber

class SignallingClient(private val listener: SignallingClientListener) {

    private fun subscribeForAnswer(id: String) {
        Timber.tag("WEB_RTC").d("subscribeForAnswer")
        Firebase.firestore
            .collection("rooms")
            .document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Timber.tag("WEB_RTC").e("error" + e.message)
                    return@addSnapshotListener
                }

                Timber.tag("WEB_RTC").d("subscribeForAnswer success ")
                if (snapshot != null && snapshot.exists()) {
                    val remoteAnswer = snapshot.data?.get("answer")
                    Timber.tag("WEB_RTC").d("subscribeForAnswer success +")


                    remoteAnswer ?: return@addSnapshotListener

                    listener.onAnswerReceived(
                        SessionDescription(
                            SessionDescription.Type.ANSWER,
                            (remoteAnswer as HashMap<String, String>)["sdp"]
                        )
                    )
                }
            }
    }

    fun createOffer(desc: SessionDescription?, id: String) {
        val offer = hashMapOf("type" to desc?.type?.name?.toLowerCase(), "sdp" to desc?.description)
        Firebase.firestore
            .collection("rooms")
            .document(id)
            .set(hashMapOf("offer" to offer))
            .addOnSuccessListener { subscribeForAnswer(id) }
    }

    fun createRoom() {
        Firebase.firestore
            .collection("rooms")
            .add(emptyMap<String, String>())
            .addOnSuccessListener {
                    documentReference -> listener.onRoomCreated(documentReference.id)
            }
    }

    fun collectIceCandidates(iceCandidate: IceCandidate?, id: String, name: String) {
        iceCandidate ?: return

        val ice = hashMapOf(
            "candidate" to iceCandidate.sdp,
            "sdpMLineIndex" to iceCandidate.sdpMLineIndex,
            "sdpMid" to iceCandidate.sdpMid)

        Firebase.firestore
            .collection("rooms")
            .document(id)
            .collection(name).add(ice)
            .addOnSuccessListener { Timber.tag("WEB_RTC").e("collectIceCandidates SUCCESS") }
            .addOnFailureListener {
                Timber.tag("WEB_RTC").e("collectIceCandidates FAILURE: " + it.message)
            }
    }

    fun getRemoteIceCandidates(id: String, name: String) {
        Firebase.firestore
            .collection("rooms")
            .document(id)
            .collection(name)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Timber.tag("WEB_RTC").e("error" + e.message)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach {
                    val data = it.document.data
                    Timber.tag("WEB_RTC").d("ice " + data)
                    listener.onIceCandidateReceived(IceCandidate(
                        data["sdpMid"] as String?,
                        (data["sdpMLineIndex"] as Long).toInt(),
                        data["candidate"] as String?
                    ))
                }
            }
    }

    fun createAnswer(desc: SessionDescription?, id: String) {
        val answer = hashMapOf("type" to desc?.type?.name?.toLowerCase(), "sdp" to desc?.description)
        Firebase.firestore
            .collection("rooms")
            .document(id)
            .set(hashMapOf("answer" to answer))
    }

    fun getRemoteOffer(roomId: String) {
        Timber.tag("WEB_RTC").e("getRemoteOffer id " + roomId)

        Firebase.firestore
            .collection("rooms")
            .document(roomId).get().addOnSuccessListener {
                val remoteOffer = it.data?.get("offer")

                remoteOffer ?: return@addOnSuccessListener

                listener.onOfferReceived(
                    SessionDescription(
                        SessionDescription.Type.OFFER,
                        (remoteOffer as HashMap<String, String>)["sdp"]
                    )
                )
            }
    }
}