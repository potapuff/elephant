package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.DBPool;
import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.ViewHelper;
import edu.sumdu.tss.elephant.model.Database;
import edu.sumdu.tss.elephant.model.DatabaseService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.sql.*;

public class SqlController extends AbstractController {

    public static final String BASIC_PAGE = "/database/{database}/sql";

    public SqlController(Javalin app) {
        super(app);
    }

    public static void show(Context context) {
        var model = currentModel(context);
        model.put("query", context.sessionAttribute("query"));
        context.sessionAttribute("query", null);
        ViewHelper.breadcrumb(context).add("SQL console");
        context.render("/velocity/sql/show.vm", model);
    }

    public static void run(Context context) {
        String query = context.formParam("query");

        StringBuilder builder = new StringBuilder(500);
        if (query == null || query.isBlank()) {
            context.result("<pre class='query'></pre>");
            return;
        } else {
            builder.append("<pre class='query'>").append(query).append("</pre>");
        }

        context.sessionAttribute("query", query);
        String dbName = context.pathParam("database");
        Database database = DatabaseService.activeDatabase(currentUser(context).getUsername(), dbName);

        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = DBPool.getConnection(database.getName()).open().getJdbcConnection();

            st = conn.createStatement();
            boolean hasResultSet = st.execute(query);
            do {
                if (hasResultSet) {
                    rs = st.getResultSet();
                    ResultSetMetaData rsmd = rs.getMetaData();

                    String[] columnLabels = new String[rsmd.getColumnCount()];
                    builder.append("<div class='datagrid'><table><thead><tr>");
                    builder.append("<th>&nbsp;#&nbsp;</th>");
                    for (int i = 1; i < columnLabels.length + 1; i++) {
                        builder.append("<th>").append(rsmd.getColumnLabel(i)).append("</th>");
                        columnLabels[i - 1] = rsmd.getColumnLabel(i);
                    }
                    builder.append("</tr><tbody>");

                    int rn = 1;
                    while (rs.next()) {
                        String alt = rn % 2 == 0 ? "<tr class='alt'>" : "<tr>";

                        builder.append(alt);
                        builder.append("<td>").append(rn++).append("</td>");
                        for (String columnLabel : columnLabels)
                            builder.append("<td>").append(rs.getString(columnLabel)).append("</td>");
                        builder.append("</tr>");
                        if (rn > 300) {
                            builder.append("<tr><td colspan=\"").append(columnLabels.length).append("\"><strong style='color: red;'>300+ (extra rows omitted)</strong></td></tr>");
                            break;
                        }
                    }
                    builder.append("</tbody></table></div>");
                } else {
                    builder.append("DDL/DML performed");
                    if (!st.isClosed()) { // thorw error on closed statement
                        int updated = st.getUpdateCount();
                        if (updated > 0) {// -1 Stands for DDL
                            builder.append(" Changed: ").append(st.getUpdateCount());
                        }
                    }
                }
                SQLWarning warning = conn.getWarnings();
                while (warning != null) {
                    builder.append("[SQL WARNING] ").append(warning);
                    warning = warning.getNextWarning();
                }
                conn.clearWarnings();
                context.result(builder.toString());
            } while (conn.createStatement().getMoreResults());
        } catch (SQLException e) {
            context.result("<strong style='color: red;'>" + e.getMessage() + "</strong>");
        } finally {
            if (rs != null) try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (st != null) try {
                st.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (conn != null) try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void register(Javalin app) {
        app.get(BASIC_PAGE, SqlController::show, UserRole.AUTHED);
        app.post(BASIC_PAGE, SqlController::run);
    }

}
