/* Released under GPL 2.0
 * (C) 2009 by folkert@vanheusden.com
 */
package com.vanheusden.sockets;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class MyServerSocket
{
	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;
	private ServerSocket serverSocket = null;

	public void getBytes(int [] out) throws Exception
	{
		for(int index=0; index<out.length; index++)
		{
			int b = inputStream.read();
			if (b == -1)
				throw new Exception("read error");

			out[index] = b;
		}
	}

	public void getBytes(byte [] out) throws Exception
	{
		int [] in = new int[out.length];

		getBytes(in);

		for(int index=0; index<out.length; index++)
			out[index] = (byte)in[index];
	}

	public void putBytes(int [] bytes) throws Exception
	{
		byte [] out = new byte[bytes.length];

		for(int index=0; index<bytes.length; index++)
			out[index] = (byte)bytes[index];

		outputStream.write(out);
	}

	public void putBytes(byte [] bytes) throws Exception
	{
		outputStream.write(bytes);
	}

	public int getU32() throws Exception
	{
		int [] in = new int[4];

		getBytes(in);

		return
			(in[0] << 24) +
			(in[1] << 16) +
			(in[2] <<  8) +
			(in[3]      );
	}

	public long getU64() throws Exception
	{
		long data1 = getU32();
		long data2 = getU32();

		return (data1 << 32) + (data2 & 0xFFFFFFFFL);
	}

	public void putU32(int data) throws Exception
	{
		int [] out = {
			((data >> 24) & 255),
			((data >> 16) & 255),
			((data >>  8) & 255),
			((data      ) & 255) };

		putBytes(out);
	}

	public void putU64(long data) throws Exception
	{
		putU32((int)(data >> 32));
		putU32(((int)data) & 0xffffffff);
	}

	public void flush() throws Exception
	{
		outputStream.flush();
	}

	public MyServerSocket(String adapter, int port) throws Exception
	{
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(adapter, port));
	}

	public void acceptConnection() throws Exception
	{
		if (socket != null)
			socket.close();

		socket = serverSocket.accept();

		socket.setKeepAlive(true);

		inputStream = socket.getInputStream();
		outputStream = socket.getOutputStream();
	}

	public void closeSocket() throws Exception
	{
		if (socket != null)
			socket.close();
	}
}
