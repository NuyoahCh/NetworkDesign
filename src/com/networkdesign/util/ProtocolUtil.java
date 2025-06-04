package com.networkdesign.util;

import com.networkdesign.protocol.FileTransferProtocol;
import java.io.*;

/**
 * 协议工具类
 * 提供协议消息的序列化和反序列化功能
 * @author spring
 */
public class ProtocolUtil {
    
    /**
     * 将协议消息序列化为字节数组
     * @param message 要序列化的协议消息
     * @return 序列化后的字节数组
     * @throws IOException 如果序列化过程中发生IO错误
     */
    public static byte[] serializeMessage(FileTransferProtocol.Message message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // 写入消息类型（1字节）
        dos.writeByte(message.getType());
        
        // 写入消息长度（4字节）
        byte[] payload = message.getPayload();
        int length = payload != null ? payload.length : 0;
        dos.writeInt(length);
        
        // 如果存在负载数据，则写入
        if (payload != null) {
            dos.write(payload);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 将字节数组反序列化为协议消息
     * @param data 要反序列化的字节数组
     * @return 反序列化后的协议消息
     * @throws IOException 如果反序列化过程中发生IO错误
     */
    public static FileTransferProtocol.Message deserializeMessage(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        
        // 读取消息类型（1字节）
        byte type = dis.readByte();
        
        // 读取消息长度（4字节）
        int length = dis.readInt();
        
        // 读取负载数据
        byte[] payload = new byte[length];
        dis.readFully(payload);
        
        return new FileTransferProtocol.Message(type, payload);
    }
    
    /**
     * 写入协议消息
     * @param out 输出流
     * @param message 要写入的消息
     * @throws IOException 如果写入过程中发生错误
     */
    public static void writeMessage(OutputStream out, FileTransferProtocol.Message message) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        
        // 写入消息类型
        dataOut.writeByte(message.getType());
        
        // 写入消息长度
        byte[] payload = message.getPayload();
        int length = payload != null ? payload.length : 0;
        dataOut.writeInt(length);
        
        // 写入消息数据
        if (payload != null) {
            dataOut.write(payload);
        }
        
        // 刷新输出流
        dataOut.flush();
    }

    /**
     * 读取协议消息
     * @param in 输入流
     * @return 读取到的消息
     * @throws IOException 如果读取过程中发生错误
     */
    public static FileTransferProtocol.Message readMessage(InputStream in) throws IOException {
        DataInputStream dataIn = new DataInputStream(in);
        
        // 读取消息类型
        byte type = dataIn.readByte();
        
        // 读取消息长度
        int length = dataIn.readInt();
        
        // 读取消息数据
        byte[] payload = null;
        if (length > 0) {
            payload = new byte[length];
            int bytesRead = 0;
            while (bytesRead < length) {
                int count = dataIn.read(payload, bytesRead, length - bytesRead);
                if (count == -1) {
                    throw new IOException("流已结束");
                }
                bytesRead += count;
            }
        }
        
        return new FileTransferProtocol.Message(type, payload);
    }
} 