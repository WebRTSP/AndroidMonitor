#pragma once

#include "WebRTSP/RtspSession/ServerSession.h"


class ClientSession : public rtsp::ServerSession
{
public:
    ClientSession(
        const WebRTCConfigPtr&,
        const CreatePeer&,
        const SendRequest&,
        const SendResponse&) noexcept;

    bool onConnected() noexcept override;
};
