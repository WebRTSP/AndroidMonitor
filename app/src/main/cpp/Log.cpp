#include "Log.h"

#include "Helpers/SpdLog.h"


static std::shared_ptr<spdlog::logger> ReStreamerLogger;

void InitLogger(spdlog::level::level_enum level)
{
    if(!ReStreamerLogger)
        ReStreamerLogger = CreateSpdLoggerSt("ReStreamer");

    ReStreamerLogger->set_level(level);
}

const std::shared_ptr<spdlog::logger>& ReStreamerLog()
{
    if(!ReStreamerLogger) {
#ifdef NDEBUG
        InitLogger(spdlog::level::info);
#else
        InitLogger(spdlog::level::debug);
#endif
    }

    return ReStreamerLogger;
}
