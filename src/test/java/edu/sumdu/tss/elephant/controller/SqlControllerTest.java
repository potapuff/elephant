package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.DBPool;
import edu.sumdu.tss.elephant.helper.Keys;
import edu.sumdu.tss.elephant.helper.utils.StringUtils;
import edu.sumdu.tss.elephant.model.Database;
import edu.sumdu.tss.elephant.model.UserService;
import io.javalin.http.HandlerType;
import io.javalin.http.util.ContextUtil;
import org.junit.jupiter.api.*;

import io.javalin.http.Context;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/*

  Much easy to test controller as http endpoint, but there is example if you want to do deeper...

  Uncomment this in pom.xml

         <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-mock</artifactId>
            <version>2.0.8</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>3.2.18.RELEASE</version>
            <scope>test</scope>
        </dependency>


 */

public class SqlControllerTest {

    @BeforeAll
    static void setUp() {
        Keys.loadParams(new File("config.properties"));
    }

    @Test
    @DisplayName("Basic test for controller SqlController/run")
    void controller_should_execute_script() {
        var database = DBPool.getConnection().open().createQuery("select * from databases limit 1").executeAndFetchFirst(Database.class);
        var user = UserService.newDefaultUser();
        user.setUsername(database.getOwner());

        var magicValue = StringUtils.uuid();
        var query = String.format("select '%s'", magicValue);

        MockHttpServletRequest request = new MockHttpServletRequest("POST","/");
        request.setMethod("POST");
        request.setContent("".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        Context context = spy(ContextUtil.init(request, response,
                               "/",
                               Map.of("database", database.getName()), //Params form query
                               HandlerType.POST,
                               Map.of(ContextUtil.maxRequestSizeKey,1_000_000L)));
        doReturn(user).when(context).sessionAttribute(Keys.SESSION_CURRENT_USER_KEY);
        doReturn(query).when(context).formParam("query");

        SqlController.run(context);
        Assertions.assertTrue(context.body().indexOf(magicValue) > 0, "should contain our magic string");
        Assertions.assertTrue(context.status() == 200, "should return with code 200");
    }

}