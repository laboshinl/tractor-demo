package ru.laboshinl.tractor;

import org.apache.commons.lang3.ArrayUtils;
import scala.collection.mutable.Map;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class Dpi {
    private static Long computeHash(byte[] ipSrc, int portSrc, byte[] ipDst, int portDst, scala.collection.mutable.Map<Long,String> result ) {
        //ArrayUtils.reverse(ipSrc);
        //ArrayUtils.reverse(ipDst);
        Long a = ByteBuffer.allocate(8).put(ipSrc).putInt(portSrc).getLong(0);
        Long b = ByteBuffer.allocate(8).put(ipDst).putInt(portDst).getLong(0);
        Long hsh;
        if ( a > b)
            hsh = a ^ b;
        else hsh = b ^ a;

        //System.out.println(String.format("%s,%s", a, b));

//        Long max = Math.max(Math.abs(a),Math.abs(b));
//        Long min = Math.min(Math.abs(a),Math.abs(b));
//        Long hsh;
//        if (Math.abs(a) > Math.abs(b)) hsh = a << 32 ^ b;
//        else hsh = b << 32 ^ a;
        // > fails at (720576361286122584,-7666630938209549893), (720576361286124394,-7666630938209549893)
        // < fails at (-6048187978612673353,5367694962257297488) , (-6048187978612673347,5367633905002217552)
        //System.out.println(String.format("%s,%s = %s", a, b, hsh));
        if (result.isDefinedAt(hsh))
            System.out.println("not Unique!");
        return hsh;
    }

    private static Long computeHash2(byte[] ipSrc, int portSrc, byte[] ipDst, int portDst, scala.collection.mutable.Map<Long,String> result) {
//        val a = ByteBuffer.allocate(8).put(ipSrc).putInt(portSrc).getLong(0)
//        val b = ByteBuffer.allocate(8).put(ipDst).putInt(portDst).getLong(0)
//
//        val d = Math.abs(a - b)
//        val min = a + (d & d >> 63)
//        val max = b - (d & d >> 63)
//
//        max << 64 | min
        Long a = ByteBuffer.allocate(8).put(ipSrc).putInt(portSrc).getLong(0);
//        BigInteger x = BigInteger.valueOf(a);
        Long b = ByteBuffer.allocate(8).put(ipDst).putInt(portDst).getLong(0);
//        BigInteger y = BigInteger.valueOf(b);
        Long d = a - b;
        Long min = a + (d & d >> 31);
        Long max = b - (d & d >> 31);
        long hsh = max << 32 | min;
        //System.out.println(String.format("%s,%s= %s", a, b, hsh));
        if (result.isDefinedAt(hsh))
            System.out.println("not Unique!");
        return hsh;
    }
    public static void main(String[] args) {
        try {
            //System.out.println(String.format("%s:%s<->%s:%s %s", ipToString(ipSrc), portSrc, ipToString(ipDst), portDst, computeHash(ipSrc, portSrc, ipDst, portDst, result)));
            Map<Long, String> result = parseIscxi("/home/laboshinl/Testbed2.csv");
            System.out.println(result.head().toString());

        }catch ( Exception e){
            e.printStackTrace();
        }

//        try {
//            parseWithLpi("/home/laboshinl/Downloads/remotepcaps/goodpcaps/holly.ndpi");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public static String ipToString(byte[] ip) {
        //ArrayUtils.reverse(ip);
        if (ip.length == 4)
            return String.format("%s.%s.%s.%s", ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF);
        else
            return "no.ip.address";
    }

    public static byte[] stringToIp(String ip) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(ip);
        byte[] bytes = addr.getAddress();
        return bytes;
    }


    public static scala.collection.mutable.Map<Long, String> parseIscxi(String file) throws  Exception {
        //xml2csv --input Testbed.xml --output Testbed2.csv --tag TestbedSatJun12 --ignore totalSourceBytes totalDestinationBytes totalDestinationPackets totalSourcePackets sourcePayloadAsBase64 sourcePayloadAsUTF destinationPayloadAsUTF direction sourceTCPFlagsDescription destinationTCPFlagsDescription startDateTime stopDateTime destinationPayloadAsBase64 protocolName --noheader
        //sed -i 's/"//g' Testbed2.csv
        scala.collection.mutable.Map<Long,String> result = new  scala.collection.mutable.HashMap<>();

        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line;
        while (true) {
            line = r.readLine();
            if (line == null) { break; }
            String[] values = line.split(",");
            try {
                byte[] ipSrc = stringToIp(values[1]);
                byte[] ipDst = stringToIp(values[3]);
                int portSrc = Integer.parseInt(values[2]);//Short.toUnsignedInt(Short.reverseBytes(Short.parseShort(values[1])));
                int portDst = Integer.parseInt(values[4]); //Short.toUnsignedInt(Short.reverseBytes(Short.parseShort(values[3])));

//                if (result.isDefinedAt(computeHash2(ipSrc, portSrc, ipDst, portDst))) {
//                    counter ++;
//                    System.out.println(String.format("not unique id %s",computeHash2(ipSrc, portSrc, ipDst, portDst)));
//                }
                result.put(computeHash(ipSrc, portSrc, ipDst, portDst, result), values[0]);
//                if(values[4].contains("Telegram"))
//                    System.out.println(String.format("%s:%s<->%s:%s %s", ipToString(ipSrc), portSrc, ipToString(ipDst), portDst, computeHash(ipSrc, portSrc, ipDst, portDst, result)));
            }
            catch (Exception e) {
                System.out.println(e);
            }
        }
        return result;
    }

    public static scala.collection.mutable.Map<Long, String> parseWithLpi(String file) throws Exception {
        scala.collection.mutable.Map<Long,String> result = new  scala.collection.mutable.HashMap<>();

        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line;
        while (true) {
            line = r.readLine();
            if (line == null) { break; }
            String[] values = line.split(",");
            try {
                byte[] ipSrc = BigInteger.valueOf((int) Long.parseLong(values[0])).toByteArray();
                int portSrc = Short.toUnsignedInt(Short.reverseBytes((short) Integer.parseInt(values[1])));//Short.toUnsignedInt(Short.reverseBytes(Short.parseShort(values[1])));
                byte[] ipDst = BigInteger.valueOf((int) Long.parseLong(values[2])).toByteArray();
                int portDst = Short.toUnsignedInt(Short.reverseBytes((short) Integer.parseInt(values[3]))); //Short.toUnsignedInt(Short.reverseBytes(Short.parseShort(values[3])));
                ArrayUtils.reverse(ipSrc);
                ArrayUtils.reverse(ipDst);
//                if (result.isDefinedAt(computeHash2(ipSrc, portSrc, ipDst, portDst))) {
//                    counter ++;
//                    System.out.println(String.format("not unique id %s",computeHash2(ipSrc, portSrc, ipDst, portDst)));
//                }
                result.put(computeHash(ipSrc, portSrc, ipDst, portDst, result), values[4]);
//                if(values[4].contains("Telegram"))
//                    System.out.println(String.format("%s:%s<->%s:%s %s", ipToString(ipSrc), portSrc, ipToString(ipDst), portDst, computeHash(ipSrc, portSrc, ipDst, portDst, result)));
            }
            catch (Exception e) {
               System.out.println(e);
            }
        }
        return result;
    }
}