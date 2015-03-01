package au.com.addstar.slackbouncer.commands;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import au.com.addstar.slackapi.Channel;
import au.com.addstar.slackapi.MessageOptions;
import au.com.addstar.slackapi.User;
import au.com.addstar.slackapi.exceptions.SlackException;
import au.com.addstar.slackbouncer.Bouncer;
import au.com.addstar.slackbouncer.BouncerPlugin;
import au.com.addstar.slackbouncer.SlackUtils;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.scheduler.ScheduledTask;

public class SlackCommandSender implements CommandSender
{
	private BouncerPlugin plugin;
	private Bouncer bouncer;
	private User user;
	private Channel channel;
	
	private boolean hasDoneTarget;
	
	private ScheduledTask sendTask;
	private List<String> messages;
	
	public SlackCommandSender(BouncerPlugin plugin, Bouncer bouncer, User user, Channel channel)
	{
		this.plugin = plugin;
		this.bouncer = bouncer;
		this.user = user;
		this.channel = channel;
		
		messages = Lists.newArrayList();
		hasDoneTarget = false;
	}
	
	@Override
	public void addGroups( String... groups ) {}
	@Override
	public void setPermission( String perm, boolean value ) {}

	@Override
	public Collection<String> getGroups()
	{
		return Collections.emptyList();
	}

	@Override
	public String getName()
	{
		return user.getName();
	}

	@Override
	public Collection<String> getPermissions()
	{
		return Collections.emptyList();
	}

	@Override
	public boolean hasPermission( String perm )
	{
		return true;
	}

	@Override
	public void removeGroups( String... groups ) {}

	@Override
	public void sendMessage( String message )
	{
		synchronized(messages)
		{
			messages.add(message);
			
			startSendDelay();
		}
	}

	@Override
	public void sendMessage( BaseComponent... message )
	{
		sendMessage(TextComponent.toLegacyText(message));
	}

	@Override
	public void sendMessage( BaseComponent message )
	{
		sendMessage(TextComponent.toLegacyText(message));
	}

	@Override
	public void sendMessages( String... message )
	{
		synchronized(messages)
		{
			for (String line : message)
				messages.add(line);
			
			startSendDelay();
		}
	}
	
	private void startSendDelay()
	{
		if (sendTask == null)
		{
			sendTask = plugin.getProxy().getScheduler().schedule(plugin, new Runnable()
			{
				@Override
				public void run()
				{
					synchronized(messages)
					{
						if (!messages.isEmpty())
						{
							String combined = Joiner.on('\n').join(messages);
							messages.clear();
							sendMessage(combined, MessageOptions.DEFAULT);
						}
						sendTask = null;
					}
				}
			}, 1, TimeUnit.SECONDS);
		}
	}
	
	public void sendMessage(String message, MessageOptions options)
	{
		if (!hasDoneTarget)
		{
			message = String.format("<@%s> %s", user.getId(), message);
			hasDoneTarget = true;
		}
		
		try
		{
			bouncer.getSlack().sendMessage(SlackUtils.toSlack(message), channel, options);
		}
		catch (IOException e)
		{
			plugin.getLogger().severe("An IOException occurred while sending a message:");
			e.printStackTrace();
		}
		catch (SlackException e)
		{
			plugin.getLogger().severe("Slack refused the message with: " + e.getMessage());
		}
	}
	
	public void sendMessage(String[] message, MessageOptions options)
	{
		if (message.length == 0)
			return;
		
		if (!hasDoneTarget)
		{
			message[0] = String.format("<@%s> %s", user.getId(), message[0]);
			hasDoneTarget = true;
		}
		
		String combined = Joiner.on('\n').join(message);
		
		try
		{
			bouncer.getSlack().sendMessage(SlackUtils.toSlack(combined), channel, options);
		}
		catch (IOException e)
		{
			plugin.getLogger().severe("An IOException occurred while sending a message:");
			e.printStackTrace();
		}
		catch (SlackException e)
		{
			plugin.getLogger().severe("Slack refused the message with: " + e.getMessage());
		}
	}
}