#pragma once

#include "WebRTSP/RtspSession/ServerSession.h"


class AgentSession : public rtsp::ServerSession
{
public:
    struct Context {
        const std::string clientId;
        std::string agentId;
        std::string accessToken;
    };

    AgentSession(
        Context*,
        const WebRTCConfigPtr&,
        const CreatePeer&,
        const SendRequest&,
        const SendResponse&) noexcept;

    bool onConnected(
        std::string&& /*clientId*/,
        std::string&& /*agentId*/,
        std::string&& /*accessToken*/) noexcept override;

private:
    Context *const _context;
};
