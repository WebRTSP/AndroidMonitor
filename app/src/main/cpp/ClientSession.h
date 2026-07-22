#pragma once

#include "WebRTSP/RtspSession/ServerSession.h"


class ClientSession : public rtsp::ServerSession
{
public:
    struct Context {
        const std::string clientId;
        std::string agentId;
        std::string accessToken;
    };

    ClientSession(
        const WebRTCConfigPtr&,
        Context* context,
        const CreatePeer&,
        const SendRequest&,
        const SendResponse&) noexcept;

    bool onConnected() noexcept override;

protected:
    bool onSetParameterResponse(
        const rtsp::Request& request,
        const rtsp::Response& response) noexcept override;

private:
    Context *const _context;

    rtsp::CSeq _registerCSeq = rtsp::InvalidCSeq;
};
