package au.com.addstar.slackbouncer;

import io.github.slackapi4j.RealTimeSession;
import io.github.slackapi4j.SlackApi;
import io.github.slackapi4j.eventlisteners.MessageListener;
import io.github.slackapi4j.events.MessageEvent;
import io.github.slackapi4j.exceptions.SlackException;
import io.github.slackapi4j.exceptions.SlackRtException;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;


public class Bouncer extends MessageListener {
  private final BouncerPlugin plugin;
  private final SlackApi slack;
  private RealTimeSession session;
  private boolean isClosing;
  private boolean isConnecting;
  private ScheduledTask checkTask;

  private final Object lockObject = new Object();

  public Bouncer(BouncerPlugin plugin) {
    this.plugin = plugin;

    slack = new SlackApi(plugin.getConfig().token);
    connect();
    startReconnectionTask();
  }

  public SlackApi getSlack() {
    return slack;
  }

  public RealTimeSession getSession() {
    return session;
  }

  public void shutdown() {
    synchronized (lockObject) {
      isClosing = true;
      if (session != null) {
        session.close();
        session = null;
      }

      if (checkTask != null) {
        checkTask.cancel();
        checkTask = null;
      }
    }
  }

  private void startReconnectionTask() {
    checkTask = plugin.getProxy().getScheduler().schedule(plugin, () -> {
      if (!isConnecting && session == null)
        connect();
    }, 10, 10, TimeUnit.SECONDS);
  }

  private void connect() {
    synchronized (lockObject) {
      if (session != null || isConnecting)
        return;

      isClosing = false;
      isConnecting = true;
    }

    ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
      synchronized (lockObject) {
        isConnecting = false;
        try {
          session = slack.startRtSession();
          session.addListener(new ConversationListener(plugin));
          session.addListener(Bouncer.this);

        } catch (SlackException e) {
          plugin.getLogger().severe("Unable to connect to Slack service: " + e.toString());
          session = null;
        } catch (SocketTimeoutException e) {
          plugin.getLogger().severe("Unable to connect to Slack service: Connection Timeout");
          session = null;

          // Retry in a few seconds
          ProxyServer.getInstance().getScheduler().schedule(plugin, () -> {
            synchronized (lockObject) {
              if (!isClosing)
                connect();
            }
          }, 5, TimeUnit.SECONDS);
        } catch (IOException e) {
          plugin.getLogger().severe("Unable to connect to Slack service:");
          e.printStackTrace();
          session = null;
        }
      }
    });
  }

  @Override
  public void onLoginComplete() {
  }

  @Override
  public void onError(SlackRtException exception) {
    plugin.getLogger().warning("An error occured: " + exception.toString());
  }

  @Override
  public void onClose() {
    synchronized (lockObject) {
      session = null;
      if (!isClosing) {
        ProxyServer.getInstance().getScheduler().schedule(plugin, () -> {
          synchronized (lockObject) {
            if (!isClosing)
              connect();
          }
        }, 5, TimeUnit.SECONDS);
      }
    }
  }

  @Override
  public void onMessage(MessageEvent event) {
    ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> plugin.onMessage(event));

  }
}
