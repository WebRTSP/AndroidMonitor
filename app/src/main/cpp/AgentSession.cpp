#include "AgentSession.h"

#include <cassert>

#include "RtspParser/RtspParser.h"

#include <format>


AgentSession::AgentSession(
    Context* context,
    const WebRTCConfigPtr& webRTCConfig,
    const CreatePeer& createPeer,
    const SendRequest& sendRequest,
    const SendResponse& sendResponse) noexcept :
    rtsp::StreamSession(webRTCConfig, createPeer, sendRequest, sendResponse),
    _context(context)
{
}

bool AgentSession::authorize(const std::unique_ptr<rtsp::Request>& requestPtr) noexcept
{
    auto it = _context->sources.find(requestPtr->uri);
    if(it == _context->sources.end())
        return false; // don't expose info about private URIs

    assert(!it->second.accessToken.empty()); // empty token not allowed
    if(it->second.accessToken.empty())
        return false;

    // TEARDOWN can be sent by signaling server itself and without auth token
    if(requestPtr->method == rtsp::Method::TEARDOWN)
        return true;

    const auto& [authType, token] =
        rtsp::ParseAuthentication(*requestPtr);

    if(authType != rtsp::Authentication::Bearer) // FIXME? only Bearer supported atm
        return false;

    return it->second.accessToken == token;
}

bool AgentSession::onDescribeRequest(
    std::unique_ptr<rtsp::Request>&& requestPtr) noexcept
{
    _pendingRequests.emplace_back(std::move(requestPtr));
    if(_iceServersRequest)
        return true;

    _iceServersRequest = requestGetParameter(
        rtsp::WildcardUri,
        rtsp::TextParametersContentType,
        "ice-servers\r\n");

    return true;
}

bool AgentSession::onGetParameterResponse(
    const rtsp::Request& request,
    const rtsp::Response& response) noexcept
{
    if(!_iceServersRequest || *_iceServersRequest != response.cseq)
        return false;

    _iceServersRequest.reset();

    if(!StreamSession::onGetParameterResponse(request, response))
        return false;

    rtsp::Parameters parameters;
    if(!rtsp::ParseParameters(response.body, &parameters))
        return false;

    WebRTCConfig::IceServers iceServers;

    auto stunServerIt = parameters.find("stun-server");
    if(parameters.end() != stunServerIt && !stunServerIt->second.empty())
        iceServers.push_back(stunServerIt->second);

    auto turnServerIt = parameters.find("turn-server");
    if(parameters.end() != turnServerIt && !turnServerIt->second.empty())
        iceServers.push_back(turnServerIt->second);

    auto turnsServerIt = parameters.find("turns-server");
    if(parameters.end() != turnsServerIt && !turnsServerIt->second.empty())
        iceServers.push_back(turnsServerIt->second);

    setWebRTCConfig(
        std::make_shared<WebRTCConfig>(
            std::move(iceServers)));

    auto pendingRequests = std::move(_pendingRequests);
    for(auto& request: pendingRequests)
        StreamSession::onDescribeRequest(std::move(request));

    return true;
}
