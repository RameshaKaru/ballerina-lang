/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.ballerinalang.stdlib.multipart;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.internal.StringUtil;
import org.ballerinalang.launcher.util.BCompileUtil;
import org.ballerinalang.launcher.util.BRunUtil;
import org.ballerinalang.launcher.util.BServiceUtil;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.mime.util.MimeUtil;
import org.ballerinalang.mime.util.MultipartDecoder;
import org.ballerinalang.model.util.StringUtils;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.stdlib.io.channels.base.Channel;
import org.ballerinalang.stdlib.io.utils.Base64ByteChannel;
import org.ballerinalang.stdlib.io.utils.Base64Wrapper;
import org.ballerinalang.stdlib.io.utils.IOConstants;
import org.ballerinalang.stdlib.utils.HTTPTestRequest;
import org.ballerinalang.stdlib.utils.MessageUtils;
import org.ballerinalang.stdlib.utils.MultipartUtils;
import org.ballerinalang.stdlib.utils.ResponseReader;
import org.ballerinalang.stdlib.utils.Services;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.Header;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Unit tests for multipart decoder.
 *
 * @since 0.963.0
 */
public class MultipartDecoderTest {
    private CompileResult serviceResult, channelResult;
    private static final String MOCK_ENDPOINT_NAME = "mockEP";

    @BeforeClass
    public void setup() {
        String sourceFilePath = "test-src/multipart/multipart-request.bal";
        serviceResult = BServiceUtil.setupProgramFile(this, sourceFilePath);
        channelResult = BCompileUtil.compileAndSetup("test-src/multipart/bytechannel-base64.bal");
    }

    @Test(description = "Test sending a multipart request as multipart/mixed with multiple body parts")
    public void testMultiplePartsForMixed() {
        String path = "/test/multipleparts";
        List<Header> headers = new ArrayList<>();
        String multipartDataBoundary = MimeUtil.getNewMultipartDelimiter();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(),
                "multipart/mixed; boundary=" + multipartDataBoundary));
        String multipartBody = "--" + multipartDataBoundary + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "Part1" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "Part2" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers,
                multipartBody);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), " -- Part1 -- Part2" + StringUtil.NEWLINE);
    }

    @Test(description = "Test sending a multipart request as multipart/form-data with multiple body parts")
    public void testMultiplePartsForFormData() {
        String path = "/test/multipleparts";
        List<Header> headers = new ArrayList<>();
        String multipartDataBoundary = MimeUtil.getNewMultipartDelimiter();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(),
                "multipart/form-data; boundary=" + multipartDataBoundary));
        String multipartBody = "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"foo\"" + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "Part1" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"filepart\"; filename=\"file-01.txt\"" + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "Part2" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers,
                multipartBody);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), " -- Part1 -- Part2" + StringUtil.NEWLINE);
    }

    @Test(description = "Test whether the requests with new multipart sub types can be decoded properly")
    public void testMultiplePartsForNewSubTypes() {
        String path = "/test/multipleparts";
        List<Header> headers = new ArrayList<>();
        String multipartDataBoundary = MimeUtil.getNewMultipartDelimiter();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(),
                "multipart/new-sub-type; boundary=" + multipartDataBoundary));
        String multipartBody = "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"foo\"" + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "Part1" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: inline" + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "Part2" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers,
                multipartBody);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), " -- Part1 -- Part2" + StringUtil.NEWLINE);
    }

    @Test(description = "Test sending a multipart request without body parts")
    public void testMultipartsWithEmptyBody() {
        String path = "/test/emptyparts";
        List<Header> headers = new ArrayList<>();
        String multipartDataBoundary = MimeUtil.getNewMultipartDelimiter();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(),
                "multipart/mixed; boundary=" + multipartDataBoundary));
        String multipartBody = "--" + multipartDataBoundary + "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers,
                multipartBody);
        InputStream inputStream = new HttpMessageDataStreamer(inRequestMsg).getInputStream();
        String error = null;
        try {
            MultipartDecoder.decodeBodyParts("multipart/mixed; boundary=" + multipartDataBoundary,
                    inputStream);
        } catch (Exception e) {
            error = e.getMessage();
        }
        Assert.assertNotNull(error);
        Assert.assertTrue(error.contains("Reached EOF, but there is no closing MIME boundary"));
    }

    @Test(description = "Test whether the nested parts can be properly decoded.")
    public void testNestedPartsForOneLevel() {
        String path = "/test/nestedparts";
        HTTPTestRequest inRequestMsg = MultipartUtils.createNestedPartRequest(path);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), "Child Part 1" + StringUtil.NEWLINE
                + "Child Part 2" + StringUtil.NEWLINE);
    }

    @Test(description = "Test multiparts when a boundary contains equal sign")
    public void testMultipartBoundaryWithEqualSign() {
        String path = "/test/multipleparts";
        List<Header> headers = new ArrayList<>();
        String multipartDataBoundary = "\"------=_Part_19_966827328.1524324134617--\"";
        String boudaryWithoutQuotes = "------=_Part_19_966827328.1524324134617--";
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(),
                "multipart/mixed; boundary=" + multipartDataBoundary));
        String multipartBody = "--" + boudaryWithoutQuotes + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "Part1" +
                "\r\n" +
                "--" + boudaryWithoutQuotes + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "Part2" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + boudaryWithoutQuotes + "--" + "\r\n";

        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers,
                multipartBody);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), " -- Part1 -- Part2" + StringUtil.NEWLINE);
    }

    @Test
    public void testBase64DecodeByteChannel() {
        String expectedValue = "Hello Ballerina!";
        BMap<String, BValue> byteChannelStruct = MultipartUtils.getByteChannelStruct(channelResult);
        byte[] encodedByteArray = Base64.getEncoder().encode(expectedValue.getBytes());
        InputStream encodedStream = new ByteArrayInputStream(encodedByteArray);
        Base64ByteChannel base64ByteChannel = new Base64ByteChannel(encodedStream);
        byteChannelStruct.addNativeData(IOConstants.BYTE_CHANNEL_NAME, new Base64Wrapper(base64ByteChannel));
        BValue[] args = new BValue[]{byteChannelStruct};
        BValue[] returnValues = BRunUtil.invoke(channelResult, "testBase64DecodeByteChannel", args);
        Assert.assertFalse(returnValues == null || returnValues.length == 0 || returnValues[0] == null,
                "Invalid return value");
        BMap<String, BValue> decodedByteChannel = (BMap<String, BValue>) returnValues[0];
        Channel byteChannel = (Channel) decodedByteChannel.getNativeData(IOConstants.BYTE_CHANNEL_NAME);
        Assert.assertEquals(StringUtils.getStringFromInputStream(byteChannel.getInputStream()), expectedValue);
    }
}
