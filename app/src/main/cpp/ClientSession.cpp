#include "ClientSession.h"


ClientSession::ClientSession(
    const WebRTCConfigPtr& webRTCConfig,
    const CreatePeer& createPeer,
    const SendRequest& sendRequest,
    const SendResponse& sendResponse) noexcept :
    rtsp::ServerSession(webRTCConfig, createPeer, sendRequest, sendResponse)
{
}

bool ClientSession::onConnected() noexcept
{
    return false;
}
