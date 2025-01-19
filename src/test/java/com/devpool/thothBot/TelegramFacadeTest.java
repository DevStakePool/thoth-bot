package com.devpool.thothBot;

import com.devpool.thothBot.doubles.commands.DummyCommandDouble;
import com.devpool.thothBot.doubles.commands.ErrorCommandDouble;
import com.devpool.thothBot.doubles.commands.LongCommandDouble;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.telegram.command.IBotCommand;
import com.devpool.thothBot.util.TelegramUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest(classes = {DummyCommandDouble.class, ErrorCommandDouble.class, LongCommandDouble.class})
@DirtiesContext
class TelegramFacadeTest {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramFacadeTest.class);

    @MockitoBean
    private TelegramBot telegramBotMock;

    @Autowired
    private List<IBotCommand> doubleCommands;

    @Captor
    private ArgumentCaptor<SendMessage> sendMessageArgCaptor;

    private TelegramFacade telegramFacade;


    @BeforeEach
    public void beforeEach() throws Exception {
        SendResponse respMock = Mockito.mock(SendResponse.class);
        Mockito.when(respMock.isOk()).thenReturn(true);
        Mockito.when(this.telegramBotMock.execute(Mockito.any())).thenReturn(respMock);
        this.telegramFacade = new TelegramFacade();
        this.telegramFacade.setBot(this.telegramBotMock);
        this.telegramFacade.setCommands(this.doubleCommands);
    }

    @Test
    public void testDummyMessage() throws Exception {
        // Testing Help command
        Update dummyCmdUpdate = TelegramUtils.buildAnyCommandUpdate("/dummy", "thor");

        this.telegramFacade.processUpdate(dummyCmdUpdate, this.telegramBotMock);

        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
    }


    @Test
    public void testTimeoutedMessage() throws Exception {
        // Testing Help command
        Update dummyCmdUpdate = TelegramUtils.buildAnyCommandUpdate("/long", "thor");

        this.telegramFacade.processUpdate(dummyCmdUpdate, this.telegramBotMock);

        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(60 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());

        SendMessage resp = sendMessages.get(0);
        Assertions.assertTrue(resp.getParameters().getOrDefault("text", "none").toString().contains("your command timed out"));

    }

    @Test
    public void testErrorMessage() throws Exception {
        // Testing Help command
        Update dummyCmdUpdate = TelegramUtils.buildAnyCommandUpdate("/error", "thor");

        this.telegramFacade.processUpdate(dummyCmdUpdate, this.telegramBotMock);

        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(60 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
        Assertions.assertTrue(sendMessages.get(0).getParameters().getOrDefault("text", "none").toString().contains("unexpected error"));

    }

    @Test
    public void testLotsOfMessage() throws Exception {
        // Testing Help command
        List<Update> updates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            updates.add(TelegramUtils.buildAnyCommandUpdate("/dummy", "thor1"));

            if (i % 25 == 0)
                updates.add(TelegramUtils.buildAnyCommandUpdate("/long", "thor2"));

            if (i % 20 == 0)
                updates.add(TelegramUtils.buildAnyCommandUpdate("/error", "thor3"));

        }

        // Execute all
        updates.forEach(u -> this.telegramFacade.processUpdate(u, this.telegramBotMock));

        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(60 * 1000)
                                .times(100 + 4 + 5))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(100 + 4 + 5, sendMessages.size());
        Assertions.assertEquals(5,
                sendMessages.stream().filter(
                        m -> m.getParameters().getOrDefault("text", "none").toString().contains("unexpected error")).count());
        Assertions.assertEquals(4,
                sendMessages.stream().filter(
                        m -> m.getParameters().getOrDefault("text", "none").toString().contains("your command timed out")).count());
    }
}
