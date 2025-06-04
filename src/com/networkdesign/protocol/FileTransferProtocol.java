package com.networkdesign.protocol;

/**
 * 文件传输协议
 * 定义消息类型和消息结构
 */
public class FileTransferProtocol {
    // 消息类型常量
    public static final byte REQUEST_FILE_LIST = 1;    // 请求文件列表
    public static final byte FILE_LIST = 2;            // 文件列表响应
    public static final byte REQUEST_FILE = 3;         // 请求文件
    public static final byte FILE_INFO = 4;            // 文件信息
    public static final byte FILE_DATA = 5;            // 文件数据
    public static final byte TRANSFER_COMPLETE = 6;    // 传输完成
    public static final byte ERROR = 7;                // 错误消息

    // 协议常量
    public static final int HEADER_SIZE = 5;           // 消息头大小（1字节类型 + 4字节长度）
    public static final int MAX_PACKET_SIZE = 8192;    // 最大数据包大小

    /**
     * 协议消息类
     */
    public static class Message {
        private final byte type;           // 消息类型
        private final byte[] payload;      // 消息数据

        public Message(byte type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }

        public byte getType() {
            return type;
        }

        public byte[] getPayload() {
            return payload;
        }
    }
} 