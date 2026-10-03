#include "ONVIFHelpers.h"

#include "gsoap/plugin/wsseapi.h"

#include "CxxPtr/GlibPtr.h"


namespace {

void AddAuth(
    SOAP& soap,
    const std::string& userName,
    const std::string& password) noexcept
{
    if(userName.empty() && password.empty())
        return;

    soap_wsse_add_UsernameTokenDigest(
        soap,
        nullptr,
        userName.c_str(),
        password.c_str());
}

}

std::string ONVIFFetchUrl(
    SOAP& soap,
    const std::string& endpoint,
    const std::string& userName,
    const std::string& password) noexcept
{
    soap_status status;

    _tds__GetCapabilities getCapabilities;
    tt__CapabilityCategory category = tt__CapabilityCategory::Media;
    getCapabilities.Category.push_back(category);
    _tds__GetCapabilitiesResponse getCapabilitiesResponse;
    AddAuth(soap, userName, password);
    status = soap_call___tds__GetCapabilities(
        soap,
        endpoint.c_str(),
        nullptr,
        &getCapabilities,
        getCapabilitiesResponse);
    if(status != SOAP_OK)
        return {};

    const std::string& mediaEndpoint = getCapabilitiesResponse.Capabilities->Media->XAddr;

    _trt__GetProfiles getProfiles;
    _trt__GetProfilesResponse getProfilesResponse;
    AddAuth(soap, userName, password);
    status = soap_call___trt__GetProfiles(
        soap,
        mediaEndpoint.c_str(),
        nullptr,
        &getProfiles,
        getProfilesResponse);
    if(status != SOAP_OK)
        return {};

    if(getProfilesResponse.Profiles.empty())
        return {};

    const tt__Profile *const mediaProfile = getProfilesResponse.Profiles[0];
    _trt__GetStreamUri getStreamUri;
    _trt__GetStreamUriResponse getStreamUriResponse;
    getStreamUri.ProfileToken = mediaProfile->token;

    tt__StreamSetup streamSetup;

    tt__Transport transport;
    transport.Protocol = tt__TransportProtocol::RTSP;

    streamSetup.Transport = &transport;

    getStreamUri.StreamSetup = &streamSetup;

    AddAuth(soap, userName, password);
    status = soap_call___trt__GetStreamUri(
        soap,
        mediaEndpoint.c_str(),
        nullptr,
        &getStreamUri,
        getStreamUriResponse);
    if(status != SOAP_OK)
        return {};

    const tt__MediaUri *const mediaUri = getStreamUriResponse.MediaUri;
    if(!mediaUri || mediaUri->Uri.empty())
        return {};

    GCharPtr uriStringPtr;
    if(!userName.empty() || !password.empty()) {
        GUriPtr uriPtr(g_uri_parse(mediaUri->Uri.c_str(), G_URI_FLAGS_ENCODED, nullptr));
        GUri* uri = uriPtr.get();
        if(!g_uri_get_user(uri) && !g_uri_get_password(uri)) {
            GCharPtr userPtr(
                !userName.empty() ?
                g_uri_escape_string(
                    userName.c_str(),
                    G_URI_RESERVED_CHARS_SUBCOMPONENT_DELIMITERS,
                    false) :
                nullptr);
            GCharPtr passwordPtr(
                !password.empty() ?
                g_uri_escape_string(
                    password.c_str(),
                    G_URI_RESERVED_CHARS_SUBCOMPONENT_DELIMITERS,
                    false) :
                nullptr);
            uriStringPtr.reset(
                g_uri_join_with_user(
                    G_URI_FLAGS_ENCODED,
                    g_uri_get_scheme(uri),
                    userPtr.get(),
                    passwordPtr.get(),
                    g_uri_get_auth_params(uri),
                    g_uri_get_host(uri),
                    g_uri_get_port(uri),
                    g_uri_get_path(uri),
                    g_uri_get_query(uri),
                    g_uri_get_fragment(uri)));
        }
    }

    return uriStringPtr ? std::string(uriStringPtr.get()) : mediaUri->Uri;
}
