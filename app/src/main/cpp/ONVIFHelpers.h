#pragma once

#include "ONVIF/SOAP.h"

std::string ONVIFFetchUrl(
    SOAP& soap,
    const std::string& endpoint,
    const std::string& userName,
    const std::string& password) noexcept;
