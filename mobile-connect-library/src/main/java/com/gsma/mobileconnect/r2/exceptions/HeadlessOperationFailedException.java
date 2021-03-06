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
package com.gsma.mobileconnect.r2.exceptions;

import com.gsma.mobileconnect.r2.MobileConnectStatus;

/**
 * Exception thrown when the headless operation fails due to too many redirects or when it times out
 *
 * @since 2.0
 */
public class HeadlessOperationFailedException extends AbstractMobileConnectException
{
    private final String message;

    /**
     * Create an instance of this exception.
     *
     * @param message HTTP method of the request.
     */
    public HeadlessOperationFailedException(final String message)
    {
        super(message);
        this.message = message;
    }

    /**
     * @return the message giving details of this exception
     */
    public String getMesage()
    {
        return this.message;
    }

    @Override
    public MobileConnectStatus toMobileConnectStatus(final String task)
    {
        return MobileConnectStatus.error("http_failure",
            String.format("%s headless operation either had too many redirects or it timed out",
                task), this);
    }
}
