/*
 * SOFTWARE USE PERMISSION
 *
 * By downloading and accessing this software and associated documentation files ("Software") you are granted the
 * unrestricted right to deal in the Software, including, without limitation the right to use, copy, modify, publish,
 * sublicense and grant such rights to third parties, subject to the following conditions:
 *
 * The following copyright notice and this permission notice shall be included in all copies, modifications or
 * substantial portions of this Software: Copyright © 2016 GSM Association.
 *
 * THE SOFTWARE IS PROVIDED "AS IS," WITHOUT WARRANTY OF ANY KIND, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. YOU AGREE TO
 * INDEMNIFY AND HOLD HARMLESS THE AUTHORS AND COPYRIGHT HOLDERS FROM AND AGAINST ANY SUCH LIABILITY.
 */
package com.gsma.mobileconnect.r2.demo;

import com.gsma.mobileconnect.r2.*;
import com.gsma.mobileconnect.r2.authentication.AuthenticationOptions;
import com.gsma.mobileconnect.r2.cache.CacheAccessException;
import com.gsma.mobileconnect.r2.cache.ConcurrentCache;
import com.gsma.mobileconnect.r2.constants.DefaultOptions;
import com.gsma.mobileconnect.r2.constants.Parameters;
import com.gsma.mobileconnect.r2.constants.Scopes;
import com.gsma.mobileconnect.r2.demo.objects.CachedParameters;
import com.gsma.mobileconnect.r2.demo.objects.OperatorParameters;
import com.gsma.mobileconnect.r2.demo.utils.Constants;
import com.gsma.mobileconnect.r2.demo.utils.ReadAndParseFiles;
import com.gsma.mobileconnect.r2.discovery.DiscoveryOptions;
import com.gsma.mobileconnect.r2.discovery.DiscoveryResponse;
import com.gsma.mobileconnect.r2.discovery.OperatorUrls;
import com.gsma.mobileconnect.r2.encoding.DefaultEncodeDecoder;
import com.gsma.mobileconnect.r2.json.IJsonService;
import com.gsma.mobileconnect.r2.json.JacksonJsonService;
import com.gsma.mobileconnect.r2.json.JsonDeserializationException;
import com.gsma.mobileconnect.r2.rest.RestClient;
import com.gsma.mobileconnect.r2.utils.HttpUtils;
import com.gsma.mobileconnect.r2.utils.LogUtils;
import com.gsma.mobileconnect.r2.utils.StringUtils;
import com.gsma.mobileconnect.r2.web.MobileConnectWebResponse;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @since 2.0
 */
@Controller
@EnableAutoConfiguration
@RequestMapping(path = "server_side_api"/*, produces = MediaType.APPLICATION_JSON_UTF8_VALUE*/)
public class DemoAppController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoAppController.class);

    private MobileConnectWebInterface mobileConnectWebInterface;
    private final IJsonService jsonService;
    private String clientName;
    private String apiVersion;
    private MobileConnectConfig mobileConnectConfig;
    private OperatorUrls operatorUrls;
    private boolean includeRequestIP;
    private ConcurrentCache cache;
    private ConcurrentCache discoveryCache;
    private RestClient restClient;
    private CachedParameters cachedParameters = new CachedParameters();
    private OperatorParameters operatorParams = new OperatorParameters();

    public DemoAppController(@Autowired final MobileConnectWebInterface mobileConnectWebInterface) {
        this.mobileConnectWebInterface = mobileConnectWebInterface;
        this.jsonService = new JacksonJsonService();
    }

    public DemoAppController() {
        this.jsonService = new JacksonJsonService();

        restClient = new RestClient.Builder().withJsonService(jsonService).withHttpClient(HttpClientBuilder.create().build()).build();
        this.getParameters();

        if (this.mobileConnectWebInterface == null) {
            this.mobileConnectWebInterface = MobileConnect.buildWebInterface(mobileConnectConfig, new DefaultEncodeDecoder(), this.cache, this.discoveryCache);
        }
    }


    public RedirectView startDiscovery(final HttpServletRequest request) {
        return startDiscovery("","","","", request);
    }

    @GetMapping("start_discovery")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    //public String startDiscovery(
    public RedirectView startDiscovery(
            @RequestParam(required = false) final String msisdn,
            @RequestParam(required = false) final String mcc,
            @RequestParam(required = false) final String mnc,
            @RequestParam(required = false) final String sourceIp,
            final HttpServletRequest request)
    {
        LOGGER.info("* Attempting discovery for msisdn={}, mcc={}, mnc={}",
                LogUtils.mask(msisdn, LOGGER, Level.INFO), sourceIp);

        this.mobileConnectWebInterface = MobileConnect.buildWebInterface(mobileConnectConfig, new DefaultEncodeDecoder(), this.cache, this.discoveryCache);

        this.getParameters();

        MobileConnectRequestOptions requestOptions =
                new MobileConnectRequestOptions.Builder()
                        .withDiscoveryOptions(new DiscoveryOptions.Builder()
                            .withClientIp(sourceIp).build())
                        .build();
        MobileConnectStatus status =
                this.mobileConnectWebInterface.attemptDiscovery(request, msisdn, mcc, mnc, true, mobileConnectConfig.getIncludeRequestIp(), requestOptions);

        //cachedParameters.setSdkSession(status.getSdkSession());
        // 1

        if (handleErrorMsg(status)) {
            status = this.mobileConnectWebInterface.attemptDiscovery(request, null, null, null, false, includeRequestIP, requestOptions);
        }

//        if (status.getDiscoveryResponse() != null && status.getDiscoveryResponse().getResponseCode() == Constants.ResponseOk)
//        {
//            String url = startAuthentication(
//                    cachedParameters.getSdkSession(),
//                    status.getDiscoveryResponse().getResponseData().getSubscriberId(),
//                    request);
//            return new RedirectView(url);
//
//        }

        if (status.getDiscoveryResponse() != null && status.getDiscoveryResponse().getResponseCode() == Constants.ResponseOk)
        {
            setCacheByRequest(msisdn, mcc, mnc, sourceIp, status.getDiscoveryResponse());

            String url = startAuthentication(
                    status.getSdkSession(),
                    status.getDiscoveryResponse().getResponseData().getSubscriberId(),
                    request,
                    msisdn, mcc, mnc);

            if (url == null) {
                return startDiscovery(null, null, null, null, request);
            }

            return new RedirectView(url);
        }

        else {
            //HttpResponse redirectUrl = getRedirectUrlWithMsgNew(status);
            //return getRedirectUrlWithMsg(status).getUrl();
            if (status.getUrl() != null) {
                return new RedirectView(status.getUrl(), true);
            }
            else {
                return startDiscovery(msisdn, mcc, mnc, null, request);
            }
        }
//        HttpResponse redirectUrl = getRedirectUrlWithMsgNew(status);
//        //return getRedirectUrlWithMsgNew_2(status);
//        return new RedirectView(status.getUrl(), true);
//        //return redirectUrl;
    }

    private void setCacheByRequest (final String msisdn, final String mcc, final String mnc, final String sourceIp,
                                    final DiscoveryResponse discoveryResponse) {
        try {
            if (msisdn != null) {
                discoveryCache.add(msisdn, discoveryResponse);
            }
            if (mcc != null && mnc != null) {
                discoveryCache.add(String.format("%s_%s", mcc, mnc), discoveryResponse);
            }
            if (sourceIp!= null) {
                discoveryCache.add(sourceIp, discoveryResponse);
            }
        } catch (CacheAccessException e) {
            LOGGER.error("Unable to access cache");
            e.printStackTrace();
        }
    }


    @GetMapping("start_manual_discovery")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public MobileConnectWebResponse startManualDiscovery (
            @RequestParam(required = false) final String subId,
            @RequestParam(required = false) final String clientId,
            @RequestParam(required = false) final String clientName,
            @RequestParam(required = false) final String clientSecret,
            final HttpServletRequest request
    ) throws JsonDeserializationException {

        LOGGER.info("* Attempting discovery for clientId={}, clientSecret={}, clientName={}",
                LogUtils.mask(clientId, LOGGER, Level.INFO), clientSecret, clientName);

        this.clientName = clientName;
        DiscoveryResponse discoveryResponse = this.mobileConnectWebInterface.generateDiscoveryManually(clientSecret,
                clientId, subId, clientName, operatorUrls);

        final MobileConnectStatus status = this.mobileConnectWebInterface.attemptManuallyDiscovery(discoveryResponse);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("start_manual_discovery_no_metadata")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public MobileConnectWebResponse startAuthenticationNoMetadata(
            @RequestParam(required = false) final String subId,
            @RequestParam(required = false) final String clientId,
            @RequestParam(required = false) final String clientSecret,
            final HttpServletRequest request) throws JsonDeserializationException {

        LOGGER.info("* Starting authentication for sdkSession={}, subscriberId={}, scope={}",
                LogUtils.mask(subId, LOGGER, Level.INFO), Scopes.MOBILECONNECT);
        OperatorUrls operatorUrlsWD = new OperatorUrls.Builder()
                .withAuthorizationUrl(operatorUrls.getAuthorizationUrl())
                .withRequestTokenUrl(operatorUrls.getRequestTokenUrl())
                .withUserInfoUrl(operatorUrls.getUserInfoUrl())
                .build();
        DiscoveryResponse discoveryResponse = this.mobileConnectWebInterface.generateDiscoveryManually(clientSecret,
                clientId, subId, "appName", operatorUrlsWD);

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.attemptManuallyDiscovery(discoveryResponse);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("get_parameters")
    @ResponseBody
    public void getParameters(
            @RequestParam(required = false) final String clientID,
            @RequestParam(required = false) final String clientSecret,
            @RequestParam(required = false) final URI discoveryURL,
            @RequestParam(required = false) final URI redirectURL,
            @RequestParam(required = false) final String xRedirect,
            @RequestParam(required = false) final String includeRequestIP,
            @RequestParam(required = false) final String apiVersion
    ) {

        LOGGER.info("* Getting parameters: clientId={}, clientSecret={}, discoveryUrl={}, redirectUrl={}, xRedirect={}, includeRequestIp={}, apiVersion={}",
                clientID, clientSecret, discoveryURL, redirectURL, xRedirect, includeRequestIP, apiVersion);
        this.apiVersion = apiVersion;
        mobileConnectConfig = new MobileConnectConfig.Builder()
                .withClientId(setValueToNullIfIsEmpty(clientID))
                .withClientSecret(setValueToNullIfIsEmpty(clientSecret))
                .withDiscoveryUrl(setValueToNullIfIsEmpty(discoveryURL))
                .withRedirectUrl(setValueToNullIfIsEmpty(redirectURL))
                .withXRedirect(setValueToNullIfIsEmpty(xRedirect.equals("True") ? "APP" : "True"))
                .withIncludeRequestIP(includeRequestIP.equals("True"))
                .build();

        this.mobileConnectWebInterface = MobileConnect.buildWebInterface(
                mobileConnectConfig,
                new DefaultEncodeDecoder(),
                new ConcurrentCache.Builder().withJsonService(this.jsonService).build(),
                new ConcurrentCache.Builder().withJsonService(this.jsonService).withMaxCacheSize(operatorParams.getMaxDiscoveryCacheSize()).build());
    }

    @GetMapping("endpoints")
    @ResponseBody
    public void endpoints (
            @RequestParam(required = false) final String authURL,
            @RequestParam(required = false) final String tokenURL,
            @RequestParam(required = false) final String userInfoURl,
            @RequestParam(required = false) final String metadata,
            @RequestParam(required = false) final URI discoveryURL,
            @RequestParam(required = false) final URI redirectURL
    ) {
        LOGGER.info("* Getting endpoints: authorizationUrl={}, tokenUrl={}, userInfoUrl={}, metadataUrl{}, discoveryUrl={}, redirectUrl={}",
                authURL, tokenURL, userInfoURl, metadata, discoveryURL, redirectURL);
        operatorUrls = new OperatorUrls.Builder()
                .withAuthorizationUrl(setValueToNullIfIsEmpty(authURL))
                .withRequestTokenUrl(setValueToNullIfIsEmpty(tokenURL))
                .withUserInfoUrl(setValueToNullIfIsEmpty(userInfoURl))
                .withProviderMetadataUri(setValueToNullIfIsEmpty(metadata))
                .build();

        MobileConnectConfig connectConfig = new MobileConnectConfig.Builder()
                .withDiscoveryUrl(setValueToNullIfIsEmpty(discoveryURL))
                .withRedirectUrl(setValueToNullIfIsEmpty(redirectURL))
                .build();

        this.mobileConnectWebInterface = MobileConnect.buildWebInterface(
                connectConfig,
                new DefaultEncodeDecoder(),
                new ConcurrentCache.Builder().withJsonService(this.jsonService).build(),
                new ConcurrentCache.Builder().withJsonService(this.jsonService).withMaxCacheSize(operatorParams.getMaxDiscoveryCacheSize()).build());

    }

    @GetMapping({"start_authentication", "start_authorization"})
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public String startAuthentication(
            @RequestParam(required = false) final String sdkSession,
            @RequestParam(required = false) final String subscriberId, final HttpServletRequest request, final String msisdn, final String mcc, final String mnc)
    {
        LOGGER.info("* Starting authentication for sdkSession={}, subscriberId={}, scope={}",
                sdkSession, LogUtils.mask(subscriberId, LOGGER, Level.INFO));

        String scope = operatorParams.getScope();
        if (scope == null && operatorUrls.getProviderMetadataUri() == null) {
            apiVersion = Constants.Version1;
        } else if (scope == null){
            apiVersion = Constants.Version2;
        }

        final MobileConnectRequestOptions options = new MobileConnectRequestOptions.Builder()
                .withAuthenticationOptions(new AuthenticationOptions.Builder()
                        .withScope(scope)
                        .withContext(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
                        .withBindingMessage(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
                        .withClientName(clientName)
                        .build())
                .build();
        final MobileConnectStatus status =
                this.mobileConnectWebInterface.startAuthentication(request, sdkSession, subscriberId,
                        null, null, options);

        if (status.getErrorMessage() != null) {
//            return String.format("Authentication was failed: '{0}' , with code: {1}", status.getErrorMessage(), status.getErrorCode());
            return null;
        }
        cachedParameters.setNonce(status.getNonce());
        if (msisdn != null) {
            try {
                discoveryCache.add(status.getState(), discoveryCache.get(msisdn));
            } catch (CacheAccessException e) {
                e.printStackTrace();
            }
        }

        if (mcc != null) {
            try {
                discoveryCache.add(status.getState(), discoveryCache.get(String.format("%s_%s", mcc, mnc)));
            } catch (CacheAccessException e) {
                e.printStackTrace();
            }
        }
        return status.getUrl();
    }

    @GetMapping("start_authentication_r1")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public MobileConnectWebResponse startAuthenticationR1(
            @RequestParam(required = false) final String sdkSession,
            @RequestParam(required = false) final String subscriberId,
            final HttpServletRequest request) {

        LOGGER.info("* Starting authentication for sdkSession={}, subscriberId={}, scope={}",
                sdkSession, LogUtils.mask(subscriberId, LOGGER, Level.INFO), Scopes.MOBILECONNECT);

        final MobileConnectRequestOptions options = new MobileConnectRequestOptions.Builder()
                .withAuthenticationOptions(new AuthenticationOptions.Builder()
                        .withScope(Scopes.MOBILECONNECT)
                        .build())
                .build();

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.startAuthentication(request, sdkSession, subscriberId,
                        null, null, options);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("headless_authentication")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public MobileConnectWebResponse headlessAuthentication(
            @RequestParam(required = false) final String sdkSession,
            @RequestParam(required = false) final String subscriberId,
            @RequestParam(required = false) final String scope, final HttpServletRequest request)
    {
        LOGGER.info("* Starting authentication for sdkSession={}, subscriberId={}, scope={}",
                sdkSession, LogUtils.mask(subscriberId, LOGGER, Level.INFO), scope);

        final MobileConnectRequestOptions options = new MobileConnectRequestOptions.Builder()
                .withAuthenticationOptions(new AuthenticationOptions.Builder()
                        .withScope(scope)
                        .withContext(apiVersion.equals(DefaultOptions.VERSION_MOBILECONNECTAUTHZ) ? "headless" : null)
                        .withBindingMessage(apiVersion.equals(DefaultOptions.VERSION_MOBILECONNECTAUTHZ) ? "demo headless" : null)
                        .build())
                .witAutoRetrieveIdentitySet(true)
                .build();

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.requestHeadlessAuthentication(request, sdkSession,
                        subscriberId, null, null, options);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("user_info")
    @ResponseBody
    public MobileConnectWebResponse requestUserInfo(
            @RequestParam(required = false) final String state,
            @RequestParam(required = false) final String accessToken, final HttpServletRequest request)
    {
        LOGGER.info("* Requesting user info for state={}, accessToken={}", state,
                LogUtils.mask(accessToken, LOGGER, Level.INFO));

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.requestUserInfo(request, cachedParameters.getSdkSession(), accessToken == null ? cachedParameters.getAccessToken() : accessToken);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("identity")
    @ResponseBody
    public MobileConnectWebResponse requestIdentity(
            @RequestParam(required = false) final String state,
            @RequestParam(required = false) final String accessToken, final HttpServletRequest request) {
        LOGGER.info("* Requesting identity info for state={}, accessToken={}", state,
                LogUtils.mask(accessToken, LOGGER, Level.INFO));

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.requestIdentity(request, cachedParameters.getSdkSession(), accessToken == null ? cachedParameters.getAccessToken() : accessToken);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("refresh_token")
    @ResponseBody
    public MobileConnectWebResponse refreshToken(
            @RequestParam(required = false) final String sdkSession,
            @RequestParam(required = false) final String refreshToken, final HttpServletRequest request) {
        LOGGER.info("* Calling refresh token for sdkSession={}, refreshToken={}", sdkSession,
                LogUtils.mask(refreshToken, LOGGER, Level.INFO));

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.refreshToken(request, refreshToken, sdkSession);

        return new MobileConnectWebResponse(status);
    }

    @GetMapping("revoke_token")
    @ResponseBody
    public MobileConnectWebResponse revokeToken(
            @RequestParam(required = false) final String sdkSession,
            @RequestParam(required = false) final String accessToken, final HttpServletRequest request) {
        LOGGER.info("* Calling revoke token for sdkSession={}, accessToken={}", sdkSession,
                LogUtils.mask(accessToken, LOGGER, Level.INFO));

        final MobileConnectStatus status =
                this.mobileConnectWebInterface.revokeToken(request, accessToken,
                        Parameters.ACCESS_TOKEN_HINT, sdkSession);

        return new MobileConnectWebResponse(status);
    }

//    @GetMapping("")
//    @ResponseBody
//    public MobileConnectWebResponse handleRedirect(
//            @RequestParam(required = false) final String sdkSession,
//            @RequestParam(required = false, value = "mcc_mnc") final String mccMnc,
//            @RequestParam(required = false) final String code,
//            @RequestParam(required = false) final String expectedState,
//            @RequestParam(required = false) final String expectedNonce,
//            @RequestParam(required = false) final String subscriberId, final HttpServletRequest request)
//    {
//        LOGGER.info(
//                "* Handling redirect for sdkSession={}, mccMnc={}, code={}, expectedState={}, expectedNonce={}, subscriberId={}",
//                sdkSession, mccMnc, code, expectedState, expectedNonce,
//                LogUtils.mask(subscriberId, LOGGER, Level.INFO));
//
//        final URI requestUri = HttpUtils.extractCompleteUrl(request);
//
//        final MobileConnectStatus status =
//                this.mobileConnectWebInterface.handleUrlRedirect(request, requestUri, sdkSession,
//                        expectedState, expectedNonce, null);
//
//        return new MobileConnectWebResponse(status);
//    }

    @GetMapping("")
    @ResponseBody
    public MobileConnectWebResponse handleRedirect(
            @RequestParam(required = false) final String sdkSession,
            @RequestParam(required = false, value = "mcc_mnc") final String mccMnc,
            @RequestParam(required = false) final String code,
            @RequestParam(required = false) final String expectedState,
            @RequestParam(required = false) final String expectedNonce,
            @RequestParam(required = false) final String subscriberId, final HttpServletRequest request)
    {
        LOGGER.info(
                "* Handling redirect for sdkSession={}, mccMnc={}, code={}, expectedState={}, expectedNonce={}, subscriberId={}",
                sdkSession, mccMnc, code, expectedState, expectedNonce,
                LogUtils.mask(subscriberId, LOGGER, Level.INFO));

        final URI requestUri = HttpUtils.extractCompleteUrl(request);
        DiscoveryResponse discoveryResponse = null;
        if (!code.isEmpty()) {
            discoveryResponse = this.discoveryCache.get(expectedState);
        }
        final MobileConnectStatus status =
                this.mobileConnectWebInterface.handleUrlRedirect(request, requestUri, discoveryResponse,
                        expectedState, expectedNonce, null);
        return new MobileConnectWebResponse(status);
    }

    private boolean handleErrorMsg(MobileConnectStatus status)
    {
        return status.getErrorMessage() != null;
    }

//    TODO move to the utility functions
    private String setValueToNullIfIsEmpty (String value) {
        if (StringUtils.isNullOrEmpty(value)) {
            return null;
        }
        return value;
    }

//    TODO move to the utility functions
    private URI setValueToNullIfIsEmpty (URI value) {
        if (value == null || value.toString().equals("")) {
            return null;
        }
        return value;
    }

//    @GetMapping("discovery_callback")
//    @ResponseBody
//    @ResponseStatus(HttpStatus.FOUND)
//    public RedirectView DiscoveryCallback(@RequestParam(required = false) final String state,
//                                                      @RequestParam(required = false) final String error,
//                                                      @RequestParam(required = false) final String description,
//                                                      @RequestParam(required = false) final String mcc_mnc,
//                                                      @RequestParam(required = false) final String subscriber_id,
//                                                      final HttpServletRequest request)
//    {
//        if (state != null || error!= null || description!= null ){
//            RedirectView view = new RedirectView();
//            view.setStatusCode(HttpStatus.OK);
//          //  MobileConnectWebResponse resp = StateDiscoveryCallback(state, error, description, request);
//            view.setEncodingScheme(StateDiscoveryCallback(state, error, description, request).toString());
//            //return new RedirectView().setStatusCode(HttpStatus.OK); new MobileConnectWebResponse(StateDiscoveryCallback(state, error, description, request));
//            return view;
//        }
//        else
//            return  mccMncDiscoveryCallback(mcc_mnc, subscriber_id, request);
//    }





//    @GetMapping("discovery_callback")
//    @ResponseBody
//    @ResponseStatus(HttpStatus.FOUND)
//    public MobileConnectWebResponse DiscoveryCallbackOld(@RequestParam(required = false) final String state, final HttpServletRequest request)
//    {
//        final MobileConnectRequestOptions options = new MobileConnectRequestOptions.Builder()
//                .withAuthenticationOptions(new AuthenticationOptions.Builder()
//                        .withContext(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
//                        .withBindingMessage(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
//                        .withClientName(clientName)
//                        .build())
//                .build();
//
//        final URI requestUri = HttpUtils.extractCompleteUrl(request);
//        MobileConnectStatus status = this.mobileConnectWebInterface.handleUrlRedirect(request, requestUri, cachedParameters.getSdkSession(), state, cachedParameters.getNonce(), options);
//
//        cachedParameters.setAccessToken(status.getRequestTokenResponse().getResponseData().getAccessToken());
//        return new MobileConnectWebResponse(status);
//    }

    @GetMapping(value = "discovery_callback", params = "mcc_mnc")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public RedirectView mccMncDiscoveryCallback(@RequestParam(required = false) final String mcc_mnc,
                                                // public String mccMncDiscoveryCallback(@RequestParam(required = false) final String mcc_mnc,
                                                @RequestParam(required = false) final String subscriber_id,
                                                final HttpServletRequest request)
    {

        final MobileConnectRequestOptions options = new MobileConnectRequestOptions.Builder()
                .withAuthenticationOptions(new AuthenticationOptions.Builder()
                        .withContext(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
                        .withBindingMessage(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
                        .withClientName(clientName)
                        .build())
                .build();

        String[] mcc_mncArray = mcc_mnc.split("_");
        String mcc = mcc_mncArray[0];
        String mnc = mcc_mncArray[1];

        MobileConnectStatus status = this.mobileConnectWebInterface.attemptDiscovery(request, null, mcc, mnc, true, mobileConnectConfig.getIncludeRequestIp(), options);

        if (status.getDiscoveryResponse() != null) {
            setCacheByRequest(null, mcc, mnc, null, status.getDiscoveryResponse());
            //MobileConnectStatus authResponse = startAuthentication( request, status.getDiscoveryResponse(), subscriber_id);
            String url = startAuthentication(
                    status.getSdkSession(),
                    subscriber_id,
                    request, null, mcc, mnc);
            return new RedirectView(url);
            //return url;
        }
        else {
            return new RedirectView(status.getUrl(), true);
            //return status.getUrl();
        }
    }

    @GetMapping(value = "discovery_callback")
    @ResponseBody
    @ResponseStatus(HttpStatus.FOUND)
    public MobileConnectWebResponse StateDiscoveryCallback(@RequestParam(required = false) String state,
    //public RedirectView StateDiscoveryCallback(@RequestParam(required = false) final String state,
                                          @RequestParam(required = false) final String error,
                                          @RequestParam(required = false) final String description,
                                          final HttpServletRequest request)
    {
        if (error != null)
        {
            return new MobileConnectWebResponse(MobileConnectStatus.error(error, description, new Exception()));
            //return new RedirectView(MobileConnectStatus.error(Constants.InvalidArgument, description, new Exception()).getUrl());
        }
        final MobileConnectRequestOptions options = new MobileConnectRequestOptions.Builder()
                .withAuthenticationOptions(new AuthenticationOptions.Builder()
                        .withContext(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
                        .withBindingMessage(apiVersion.equals(Constants.Version2) ? Constants.ContextBindingMsg : null)
                        .withClientName(clientName)
                        .build())
                .build();

        URI requestUri = HttpUtils.extractCompleteUrl(request);
        //MobileConnectStatus status = this.mobileConnectWebInterface.handleUrlRedirect(request, requestUri, cachedParameters.getSdkSession(), state, cachedParameters.getNonce(), options);
        MobileConnectStatus status = null;
        status = this.mobileConnectWebInterface.handleUrlRedirect(request, requestUri, discoveryCache.get(state), state, cachedParameters.getNonce(), options);

        cachedParameters.setAccessToken(status.getRequestTokenResponse().getResponseData().getAccessToken());
        return new MobileConnectWebResponse(status);
        //return new RedirectView(status.getUrl());
    }

    private void getParameters() {
        operatorParams = ReadAndParseFiles.ReadFile(Constants.ConfigFilePath);
        apiVersion = operatorParams.getApiVersion();
        includeRequestIP = operatorParams.getIncludeRequestIP().equals("True");
        cache = new ConcurrentCache.Builder()
                .withJsonService(this.jsonService)
                .withMaxCacheSize(operatorParams.getMaxDiscoveryCacheSize())
                .build();

        discoveryCache = new ConcurrentCache.Builder().withJsonService(this.jsonService).withMaxCacheSize(operatorParams.getMaxDiscoveryCacheSize()).build();
        try {
            mobileConnectConfig = new MobileConnectConfig.Builder()
                    .withClientId(operatorParams.getClientID())
                    .withClientSecret(operatorParams.getClientSecret())
                    .withDiscoveryUrl(new URI(operatorParams.getDiscoveryURL()))
                    .withRedirectUrl(new URI(operatorParams.getRedirectURL()))
                    .withXRedirect(operatorParams.getXRedirect().equals("True") ? "APP" : "False")
                    .build();
        } catch (URISyntaxException e) {
            LOGGER.error("Wrong URI provided");
            e.printStackTrace();
        }
    }

//    private MobileConnectStatus getRedirectUrlWithMsg(MobileConnectStatus status) {
//        HttpResponseFactory factory = new DefaultHttpResponseFactory();
//        HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HTTP_1_1, HttpStatus.FOUND.value(), null), null);
//        response.addHeader("Location", status.getUrl());
//
//        String statusUrl = status.getUrl();
//        if (statusUrl != null)
//        {
//           // MobileConnectWebResponse ddd = new MobileConnectWebResponse(status);
//            return MobileConnectStatus.operatorSelection(statusUrl);
//        }
//        // String authResponse = MobileConnectStatus.
//        // HttpHeaders headers = new HttpHeaders();
//        // headers.add(HttpHeaders.LOCATION, statusUrl);
//        return MobileConnectStatus.error(Constants.InvalidArgument, status.getErrorMessage(), new Exception());
//    }

//    private HttpResponse getRedirectUrlWithMsgNew(MobileConnectStatus status) {
//        HttpResponseFactory factory = new DefaultHttpResponseFactory();
//        HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HTTP_1_1, HttpStatus.FOUND.value(), null), null);
//       // MobileConnectWebResponse response = new MobileConnectWebResponse(status);
////        HttpServletResponse
////
//        response.addHeader("Location", status.getUrl());
//        return response;
////        String statusUrl = status.getUrl();
////        if (statusUrl != null)
////        {
////            MobileConnectWebResponse ddd = new MobileConnectWebResponse(status);
////            return MobileConnectStatus.operatorSelection(statusUrl);
////        }
////        // String authResponse = MobileConnectStatus.
////        // HttpHeaders headers = new HttpHeaders();
////        // headers.add(HttpHeaders.LOCATION, statusUrl);
////        return MobileConnectStatus.error(Constants.InvalidArgument, status.getErrorMessage(), new Exception());
//    }
//
//    private ModelAndView getRedirectUrlWithMsgNew_2(MobileConnectStatus status) {
//        ModelMap model = new ModelMap();
//        model.addAttribute("Location", status.getUrl());
//        ModelAndView view = new ModelAndView("redirection", model);
//        view.setStatus(HttpStatus.FOUND);
//        return view;
//    }
}