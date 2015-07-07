/* Released under GPL 2.0
 * (C) 2009 by folkert@vanheusden.com
 */
package com.vanheusden.sockets;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MyHTTPServer
{
	private Socket socket;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;
	private ServerSocket serverSocket = null;

	public MyHTTPServer(String adapter, int port) throws Exception
	{
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(adapter, port));
	}

	public List<String> acceptConnectionGetRequest() throws Exception
	{
		socket = serverSocket.accept();

		socket.setKeepAlive(true);

		inputStream  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

		List<String> request = new ArrayList<String>();
		for(;;)
		{
			String line = inputStream.readLine();

			if (line.equals(""))
				break;

			request.add(line);
		}

		return request;
	}

	public void sendReply(List<String> reply) throws Exception
	{
		for(String currentLine : reply)
			outputStream.write(currentLine, 0, currentLine.length());

		outputStream.flush();

		socket.close();
	}
}
