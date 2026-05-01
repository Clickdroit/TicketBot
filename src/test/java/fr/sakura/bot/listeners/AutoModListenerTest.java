package fr.sakura.bot.listeners;

import fr.sakura.bot.core.service.SpamDetector;
import fr.sakura.bot.core.service.TempBanService;
import fr.sakura.bot.core.store.AutoModRuleStore;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class AutoModListenerTest {

    private ModerationLogListener logListener;
    private SettingsManager settings;
    private SpamDetector spamDetector;
    private TempBanService tempBanService;
    private AutoModRuleStore ruleStore;
    private AutoModListener listener;

    @BeforeEach
    void setUp() {
        logListener = mock(ModerationLogListener.class);
        settings = mock(SettingsManager.class);
        spamDetector = mock(SpamDetector.class);
        tempBanService = mock(TempBanService.class);
        ruleStore = mock(AutoModRuleStore.class);
        listener = new AutoModListener(logListener, settings, spamDetector, tempBanService, ruleStore);
    }

    @Test
    void onMessageReceived_SpamDetected() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Message message = mock(Message.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getMessage()).thenReturn(message);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getAuthor()).thenReturn(user);
        when(event.getChannel()).thenReturn(channel);
        when(user.isBot()).thenReturn(false);
        when(guild.getId()).thenReturn("guild-1");
        when(member.getId()).thenReturn("user-1");
        when(user.getId()).thenReturn("user-1");
        when(message.getContentRaw()).thenReturn("spam spam spam");

        when(settings.isAntiSpamEnabled("guild-1")).thenReturn(true);
        when(spamDetector.check(event, settings)).thenReturn(true);
        when(message.delete()).thenReturn(mock(AuditableRestAction.class));
        when(settings.getAutomodNoticeCooldownSeconds("guild-1")).thenReturn(10);
        when(spamDetector.getStrikes("guild-1", "user-1")).thenReturn(1);
        
        MessageCreateAction msgAction = mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(msgAction);

        listener.onMessageReceived(event);

        verify(message).delete();
    }

    @Test
    void onMessageReceived_LinkForbidden() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Message message = mock(Message.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getMessage()).thenReturn(message);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getAuthor()).thenReturn(user);
        when(event.getChannel()).thenReturn(channel);
        when(user.isBot()).thenReturn(false);
        when(guild.getId()).thenReturn("guild-1");
        when(member.getId()).thenReturn("user-1");
        when(user.getId()).thenReturn("user-1");
        when(message.getContentRaw()).thenReturn("Check this https://google.com");

        when(settings.isAntiLinkEnabled("guild-1")).thenReturn(true);
        when(settings.isGifLinksAllowed("guild-1")).thenReturn(false);
        when(settings.getAutomodNoticeCooldownSeconds("guild-1")).thenReturn(10);
        
        when(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(false);
        when(message.delete()).thenReturn(mock(AuditableRestAction.class));
        
        MessageCreateAction msgAction = mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(msgAction);

        listener.onMessageReceived(event);

        verify(message).delete();
        // Use generic matchers for logAction to avoid brittle test
        verify(logListener).logAction(any(), anyString(), any(), any(Member.class), anyString(), anyString());
    }
}
