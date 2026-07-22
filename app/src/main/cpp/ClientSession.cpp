#include "ClientSession.h"

#include <format>


ClientSession::ClientSession(
    const WebRTCConfigPtr& webRTCConfig,
    Context* context,
    const CreatePeer& createPeer,
    const SendRequest& sendRequest,
    const SendResponse& sendResponse) noexcept :
    rtsp::ServerSession(webRTCConfig, createPeer, sendRequest, sendResponse),
    _context(context)
{
}

bool ClientSession::onConnected() noexcept
{
    if(_context->agentId.empty() || _context->accessToken.empty()) {
        _registerCSeq = requestSetParameter(
            rtsp::WildcardUri,
            rtsp::TextParametersContentType,
            std::format("register: {}\r\n", _context->clientId)
        );

        return true;
    } else {
    }
}

bool ClientSession::onSetParameterResponse(
    const rtsp::Request& request,
    const rtsp::Response& response) noexcept
{
    if(!rtsp::ServerSession::onSetParameterResponse(request, response))
        return false;

    return true;
}
