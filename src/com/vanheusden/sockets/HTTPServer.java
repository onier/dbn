package com.vanheusden.sockets;


import com.vanheusden.sockets.MyHTTPServer;

import java.util.ArrayList;
import java.util.List;

class HTTPServer implements Runnable {

    String adapter;
    int port;
    int webServerHits, webServer404;

    public HTTPServer(String adapter, int port) {
        this.adapter = adapter;
        this.port = port;
    }

    public void addPageHeader(List<String> whereTo) {
        whereTo.add("<HTML><BODY><table width=\"100%\" bgcolor=\"#000000\" cellpadding=\"0\" cellspacing=\"0\"><tr><td><A HREF=\"/\"><img src=\"http://www.vanheusden.com/images/vanheusden02.jpg?source=coffeesaint\" BORDER=\"0\"></A></td></tr></table><BR>\n");
        // whereTo.add("<TABLE><TR VALIGN=TOP><TD VALIGN=TOP ALIGN=LEFT WIDTH=225><IMG SRC=\"http://vanheusden.com/java/CoffeeSaint/coffeesaint.jpg?source=coffeesaint\" BORDER=\"0\" ALT=\"logo (C) Bas Schuiling\"></TD><TD ALIGN=LEFT>\n");
        whereTo.add("<TABLE><TR VALIGN=TOP><TD ALIGN=LEFT>\n");

        whereTo.add("<BR><H1>" + JNbd.version + "</H1><BR><BR>");
    }

    public void addPageTail(List<String> whereTo, boolean mainMenu) {
        if (mainMenu) {
            whereTo.add("<BR><BR><BR><A HREF=\"/\">Back to main menu</A></TD></TR></TABLE></BODY></HTML>");
        } else {
            whereTo.add("<BR><BR><BR></TD></TR></TABLE></BODY></HTML>");
        }
    }

    public void sendReply_root(MyHTTPServer socket) throws Exception {
        List<String> reply = new ArrayList<String>();

        reply.add("HTTP/1.0 200 OK\r\n");
        reply.add("Connection: close\r\n");
        reply.add("Content-Type: text/html\r\n");
        reply.add("\r\n");
        addPageHeader(reply);
        reply.add("<A HREF=\"/cgi-bin/statistics.cgi\">statistics</A>");
        addPageTail(reply, false);

        socket.sendReply(reply);
    }

    public void sendReply_cgibin_statistics_cgi(MyHTTPServer socket) throws Exception {
        List<String> reply = new ArrayList<String>();

        reply.add("HTTP/1.0 200 OK\r\n");
        reply.add("Connection: close\r\n");
        reply.add("Content-Type: text/html\r\n");
        reply.add("\r\n");
        addPageHeader(reply);
        JNbd.statisticsSemaphore.acquire();
        reply.add("<TABLE>\n");
        reply.add("<TR><TD>Total running time:</TD><TD>" + ((double) (System.currentTimeMillis() - JNbd.runningSince) / 1000.0) + "s</TD></TR>\n");
        reply.add("<TR><TD>Number of webserver hits:</TD><TD>" + webServerHits + "</TD></TR>\n");
        reply.add("<TR><TD>Number of 404 pages serverd:</TD><TD>" + webServer404 + "</TD></TR>\n");
        reply.add("<TR><TD>Total number of sessions:</TD><TD>" + JNbd.nSessions + "</TD></TR>\n");
        reply.add("<TR><TD>Total sessions length:</TD><TD>" + JNbd.totalSessionsLength + "</TD></TR>\n");
        reply.add("<TR><TD>Average sessions length:</TD><TD>" + ((double) JNbd.totalSessionsLength / (double) JNbd.nSessions) + "</TD></TR>\n");
        reply.add("<TR><TD>Total time idle:</TD><TD>" + JNbd.totalIdleTime + "</TD></TR>\n");
        reply.add("<TR><TD>Total bytes written:</TD><TD>" + JNbd.totalBytesWritten + "</TD></TR>\n");
        reply.add("<TR><TD>Average bytes written:</TD><TD>" + ((double) JNbd.totalBytesWritten / (double) JNbd.nSessions) + "</TD></TR>\n");
        reply.add("<TR><TD>Total bytes read:</TD><TD>" + JNbd.totalBytesRead + "</TD></TR>\n");
        reply.add("<TR><TD>Average bytes read:</TD><TD>" + ((double) JNbd.totalBytesRead / (double) JNbd.nSessions) + "</TD></TR>\n");
        reply.add("<TR><TD>Total processing time:</TD><TD>" + JNbd.totalProcessingTime + "</TD></TR>\n");
        reply.add("<TR><TD>Average processing time per command:</TD><TD>" + ((double) JNbd.totalProcessingTime / (double) JNbd.totalNCommands) + "</TD></TR>\n");
        reply.add("<TR><TD>Total number of commands:</TD><TD>" + JNbd.totalNCommands + "</TD></TR>\n");
        reply.add("<TR><TD>Total number of reads:</TD><TD>" + JNbd.totalNRead + "</TD></TR>\n");
        reply.add("<TR><TD>Average number of reads per session:</TD><TD>" + ((double) JNbd.totalNRead / (double) JNbd.nSessions) + "</TD></TR>\n");
        reply.add("<TR><TD>Total number of writes:</TD><TD>" + JNbd.totalNWrite + "</TD></TR>\n");
        reply.add("<TR><TD>Average number of writes per session:</TD><TD>" + ((double) JNbd.totalNWrite / (double) JNbd.nSessions) + "</TD></TR>\n");
        reply.add("<TR><TD>Total number of exceptions:</TD><TD>" + JNbd.nExceptions + "</TD></TR>\n");
        JNbd.statisticsSemaphore.release();
        reply.add("</TABLE>\n");
        addPageTail(reply, true);

        socket.sendReply(reply);
    }

    public void sendReply_404(MyHTTPServer socket, String url) throws Exception {
        List<String> reply = new ArrayList<String>();

        reply.add("HTTP/1.0 404 Url not known\r\n");
        reply.add("Connection: close\r\n");
        reply.add("Content-Type: text/html\r\n");
        reply.add("\r\n");
        addPageHeader(reply);
        reply.add("URL \"" + url + "\" not known!");
        addPageTail(reply, true);

        socket.sendReply(reply);
    }

    public void run() {
        MyHTTPServer socket;

        try {
            socket = new MyHTTPServer(adapter, port);

            for (;;) {
                try {
                    List<String> request = socket.acceptConnectionGetRequest();
                    String url = request.get(0).substring(4).trim();
                    int space = url.indexOf(" ");
                    if (space != -1) {
                        url = url.substring(0, space);
                    }

                    webServerHits++;

                    if (url.equals("/") || url.equals("/index.html")) {
                        sendReply_root(socket);
                    } else if (url.equals("/cgi-bin/statistics.cgi")) {
                        sendReply_cgibin_statistics_cgi(socket);
                    } else {
                        sendReply_404(socket, url);
                        webServer404++;
                    }
                } catch (Exception e) {
                    System.err.println("Exception during command processing");
                    JNbd.showException(e);
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot create listen socket: " + e);
            JNbd.showException(e);
            System.exit(127);
        }
    }
}