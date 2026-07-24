#include "AgentSession.h"

#include <format>


AgentSession::AgentSession(
    Context* context,
    const WebRTCConfigPtr& webRTCConfig,
    const CreatePeer& createPeer,
    const SendRequest& sendRequest,
    const SendResponse& sendResponse) noexcept :
    rtsp::ServerSession(webRTCConfig, createPeer, sendRequest, sendResponse),
    _context(context)
{
}

bool AgentSession::onConnected(
    std::string&& clientId,
    std::string&& agentId,
    std::string&& accessToken) noexcept
{
    assert(_context->clientId == clientId);
    assert(_context->agentId.empty() || _context->agentId == agentId);

    _context->agentId = std::move(agentId);
    _context->accessToken = std::move(accessToken);

    // TODO! send notification to owner with agentId/accessToken

    return true;
}
