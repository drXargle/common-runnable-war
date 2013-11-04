package nz.ac.auckland.war;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * A wrapper that rewrites a ping URL in the global URL namespace
 * into the namespace context of a web application so that load
 * balancer probe URLs can be configured independently of the
 * webapp context path.
 */
public final class PingRewriteHandler extends HandlerWrapper {

  private final String url;

  public PingRewriteHandler(ContextHandler context, String url) {
    this.url = url;
    setHandler(context);
  }

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    super.handle(target, baseRequest, request, response);
    if (isStarted() && url.equals(target)) {
      Handler nested = getHandler();

      if (nested instanceof ContextHandler){
        target = ((ContextHandler) nested).getContextPath() + url;
        baseRequest.setRequestURI(target);
        baseRequest.setPathInfo(target);
      }
    }
  }
}
