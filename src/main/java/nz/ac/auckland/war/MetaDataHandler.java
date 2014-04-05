package nz.ac.auckland.war;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;


/**
 * Updates the request to use the server name, port, and scheme from
 * <code>X-Forwarded-*</code> headers provided by a trusted reverse proxy.
 */
public class MetaDataHandler extends HandlerWrapper {
  private static final Logger log = LoggerFactory.getLogger(MetaDataHandler.class);

  private static final String CLIENT_HEADER = "X-Forwarded-For";
  private static final String HOST_HEADER = "X-Forwarded-Host";
  private static final String SCHEME_HEADER = "X-Forwarded-Proto";

  private static final String HTTP_SCHEME = "http";
  private static final String HTTPS_SCHEME = "https";
  private static final int HTTP_PORT = 80;
  private static final int HTTPS_PORT = 443;

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    // handle ASYNC as well?
    if (((Request) request).getDispatcherType() == DispatcherType.REQUEST) {
      rewriteProxyRequest(baseRequest);
    }

    super.handle(target, baseRequest, request, response);
  }


  public static void rewriteProxyRequest(Request request) {
    String client = request.getHeader(CLIENT_HEADER);
    if (client != null && client.length() > 0) {
      InetSocketAddress realClient = new InetSocketAddress(client, 80);
      request.setRemoteAddr(realClient);
    }

    String scheme = request.getHeader(SCHEME_HEADER);
    if (scheme == null || scheme.equals("")) {
      scheme = HTTP_SCHEME;
    }

    request.setScheme(scheme);

    String hostAndPort = request.getHeader(HOST_HEADER);
    if (hostAndPort != null) {
      int ofs = hostAndPort.indexOf(':');
      if (ofs >= 0) {
        request.setServerName(hostAndPort.substring(0, ofs));
        request.setServerPort(Integer.parseInt(hostAndPort.substring(ofs + 1)));
      } else {
        request.setServerName(hostAndPort);
        request.setServerPort(HTTPS_SCHEME.equalsIgnoreCase(scheme) ? HTTPS_PORT : HTTP_PORT);
      }
    }
  }


}
