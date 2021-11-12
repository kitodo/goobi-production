/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.el.ValueExpression;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.primefaces.application.resource.BaseDynamicContentHandler;
import org.primefaces.model.StreamedContent;
import org.primefaces.util.Constants;

public class RangeStreamedContentHandler extends BaseDynamicContentHandler {

    private static final int DEFAULT_BUFFER_SIZE = 20480; // ..bytes = 20KB.

    private static final String MULTIPART_BOUNDARY = "MULTIPART_BYTERANGES";

    private static final Logger LOGGER = Logger.getLogger(RangeStreamedContentHandler.class.getName());

    @Override
    public void handle(FacesContext context) throws IOException {
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String library = params.get("ln");
        String resourceKey = params.get(Constants.DYNAMIC_CONTENT_PARAM);

        if (resourceKey != null && library != null && library.equals(Constants.LIBRARY)) {
            StreamedContent streamedContent = null;
            boolean cache = Boolean.parseBoolean(params.get(Constants.DYNAMIC_CONTENT_CACHE_PARAM));

            try {
                ExternalContext externalContext = context.getExternalContext();
                Map<String, Object> session = externalContext.getSessionMap();
                Map<String, String> dynamicResourcesMapping = (Map) session.get(Constants.DYNAMIC_RESOURCES_MAPPING);

                if (dynamicResourcesMapping != null) {
                    String dynamicContentEL = dynamicResourcesMapping.get(resourceKey);

                    if (dynamicContentEL != null) {
                        ValueExpression ve = context.getApplication().getExpressionFactory()
                                .createValueExpression(context.getELContext(), dynamicContentEL, StreamedContent.class);
                        streamedContent = (StreamedContent) ve.getValue(context.getELContext());

                        if (streamedContent == null || streamedContent.getStream() == null) {
                            if (externalContext.getRequest() instanceof HttpServletRequest) {
                                externalContext.responseSendError(HttpServletResponse.SC_NOT_FOUND,
                                        ((HttpServletRequest) externalContext.getRequest()).getRequestURI());
                            } else {
                                externalContext.responseSendError(HttpServletResponse.SC_NOT_FOUND, null);
                            }
                            return;
                        }

                        handleCache(externalContext, cache);
                        process(streamedContent, externalContext);
                    }
                }

                externalContext.responseFlushBuffer();
                context.responseComplete();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in streaming dynamic resource.", e);
                throw new IOException(e);
            } finally {
                // cleanup
                if (streamedContent != null && streamedContent.getStream() != null) {
                    streamedContent.getStream().close();
                }
            }
        }
    }

    private void process(StreamedContent streamedContent, ExternalContext externalContext) throws IOException {
        externalContext.setResponseStatus(HttpServletResponse.SC_OK);

        if (streamedContent.getContentEncoding() != null) {
            externalContext.setResponseHeader("Content-Encoding", streamedContent.getContentEncoding());
        }

        // Adapt implementation of Warren Dew
        // (https://stackoverflow.com/questions/28427339/how-to-implement-http-byte-range-requests-in-spring-mvc)
        // using org.primefaces.application.resource.StreamedContentHandler

        HttpServletResponse response = (HttpServletResponse) externalContext.getResponse();

        if (streamedContent.getName() != null) {
            response.setHeader("Content-Disposition",
                "inline;filename=\"" + streamedContent.getName() + "\"");
            response.setHeader("ETag", streamedContent.getName());
        }

        response.setHeader("Accept-Ranges", "bytes");
        response.setBufferSize(DEFAULT_BUFFER_SIZE);

        processInputStreamToOutputStream((HttpServletRequest) externalContext.getRequest(), response, streamedContent,
            externalContext.getResponseOutputStream());
    }

    private void processInputStreamToOutputStream(HttpServletRequest request, HttpServletResponse response,
            StreamedContent streamedContent, OutputStream outputStream) throws IOException {
        InputStream inputStream = streamedContent.getStream();
        // Prepare some variables. The full Range represents the complete file.
        int length = inputStream.available(); // Length of file
        Range full = new Range(0, length - 1, length);
        List<Range> ranges = getRanges(request, response, length, streamedContent.getName());

        if (ranges.isEmpty() || ranges.get(0) == full) {
            // Return full file.
            LOGGER.info("Return full file");
            response.setContentType(streamedContent.getContentType());
            response.setHeader("Content-Range", "bytes " + full.start + "-" + full.end + "/" + full.total);
            response.setHeader("Content-Length", String.valueOf(full.length));
            Range.copy(inputStream, outputStream, length, full.start, full.length);
        } else if (ranges.size() == 1) {
            // Return single part of file.
            Range r = ranges.get(0);
            LOGGER.info("Return 1 part of file : from (" + r.start + ") to (" + r.end + ")");
            response.setContentType(streamedContent.getContentType());
            response.setHeader("Content-Range", "bytes " + r.start + "-" + r.end + "/" + r.total);
            response.setHeader("Content-Length", String.valueOf(r.length));
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.
            // Copy single part range.
            Range.copy(inputStream, outputStream, length, r.start, r.length);
        } else {
            // Return multiple parts of file.
            response.setContentType("multipart/byteranges; boundary=" + MULTIPART_BOUNDARY);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.

            // Cast back to ServletOutputStream to get the easy println methods.
            ServletOutputStream sos = (ServletOutputStream) outputStream;

            // Copy multi part range.
            for (Range r : ranges) {
                LOGGER.info("Return multi part of file : from (" + r.start + ") to (" + r.end + ")");
                // Add multipart boundary and header fields for every range.
                sos.println();
                sos.println("--" + MULTIPART_BOUNDARY);
                sos.println("Content-Type: " + streamedContent.getContentType());
                sos.println("Content-Range: bytes " + r.start + "-" + r.end + "/" + r.total);

                // Copy single part range of multi part range.
                Range.copy(inputStream, outputStream, length, r.start, r.length);
            }

            // End with multipart boundary.
            sos.println();
            sos.println("--" + MULTIPART_BOUNDARY + "--");
        }
    }

    private static List<Range> getRanges(HttpServletRequest request, HttpServletResponse response, int length,
            String fileName) throws IOException {
        Range full = new Range(0, length - 1, length);
        List<Range> ranges = new ArrayList<>();
        // Validate and process Range and If-Range headers.
        String range = request.getHeader("Range");
        if (range != null) {
            // Range header should match format "bytes=n-n,n-n,n-n...". If not, then return
            // 416.
            if (!range.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")) {
                response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return ranges;
            }
            String ifRange = request.getHeader("If-Range");
            if (ifRange != null && !ifRange.equals(fileName)) {
                try {
                    long ifRangeTime = request.getDateHeader("If-Range"); // Throws IAE if invalid.
                    if (ifRangeTime != -1) {
                        ranges.add(full);
                    }
                } catch (IllegalArgumentException ignore) {
                    ranges.add(full);
                }
            }
            // If any valid If-Range header, then process each part of byte range.
            if (ranges.isEmpty()) {
                for (String part : range.substring(6).split(",")) {
                    // Assuming a file with length of 100, the following examples returns bytes at:
                    // 50-80 (50 to 80), 40- (40 to length=100), -20 (length-20=80 to length=100).
                    long start = Range.sublong(part, 0, part.indexOf("-"));
                    long end = Range.sublong(part, part.indexOf("-") + 1, part.length());
                    if (start == -1) {
                        start = length - end;
                        end = length - 1;
                    } else if (end == -1 || end > length - 1) {
                        end = length - 1;
                    }
                    // Check if Range is syntactically valid. If not, then return 416.
                    if (start > end) {
                        response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
                        response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        return ranges;
                    }
                    // Add range.
                    ranges.add(new Range(start, end, length));
                }
            }
        }
        return ranges;
    }

    private static class Range {
        long start;
        long end;
        long length;
        long total;

        /**
         * Construct a byte range.
         * 
         * @param start
         *            Start of the byte range.
         * @param end
         *            End of the byte range.
         * @param total
         *            Total length of the byte source.
         */
        public Range(long start, long end, long total) {
            this.start = start;
            this.end = end;
            this.length = end - start + 1;
            this.total = total;
        }

        public static long sublong(String value, int beginIndex, int endIndex) {
            String substring = value.substring(beginIndex, endIndex);
            return (substring.length() > 0) ? Long.parseLong(substring) : -1;
        }

        private static void copy(InputStream input, OutputStream output, long inputSize, long start, long length)
                throws IOException {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int read;

            if (inputSize == length) {
                // Write full range.
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } else {
                input.skip(start);
                long toRead = length;

                while ((read = input.read(buffer)) > 0) {
                    toRead -= read;
                    if (toRead > 0) {
                        output.write(buffer, 0, read);
                        output.flush();
                    } else {
                        output.write(buffer, 0, (int) toRead + read);
                        output.flush();
                        break;
                    }
                }
            }
        }
    }

}
