#pragma once

#include <deque>

#include "RtspSession/StreamSession.h"


class AgentSession : public rtsp::StreamSession
{
public:
    struct Source {
        const std::string url;
        const std::string accessToken;
    };

    typedef std::map<std::string, Source> Sources;

    struct Context {
        const std::string clientId;
        std::string agentId;
        std::string accessToken;

        Sources sources;
    };

    AgentSession(
        Context*,
        const WebRTCConfigPtr&,
        const CreatePeer&,
        const SendRequest&,
        const SendResponse&) noexcept;

protected:
    bool authorize(const std::unique_ptr<rtsp::Request>&) noexcept override;

    bool onDescribeRequest(std::unique_ptr<rtsp::Request>&&) noexcept override;

    bool onGetParameterResponse(
        const rtsp::Request&,
        const rtsp::Response&) noexcept override;

private:
    Context *const _context;

    std::optional<rtsp::CSeq> _iceServersRequest;
    std::deque<std::unique_ptr<rtsp::Request>> _pendingRequests;
};
