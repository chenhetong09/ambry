package com.github.ambry.messageformat;

import com.github.ambry.utils.ByteBufferInputStream;
import com.github.ambry.utils.Crc32;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;


public class MessageFormatRecordTest {
  @Test
  public void deserializeTest() {
    try {
      // Test Blob property V1 Record
      BlobProperties properties = new BlobProperties(1234, "id", "member", "test", true, 1234);
      ByteBuffer stream =
          ByteBuffer.allocate(MessageFormatRecord.BlobProperties_Format_V1.getBlobPropertiesRecordSize(properties));
      MessageFormatRecord.BlobProperties_Format_V1.serializeBlobPropertiesRecord(stream, properties);
      stream.flip();
      BlobProperties result =
          MessageFormatRecord.deserializeBlobProperties(new ByteBufferInputStream(stream)).getBlobProperties();
      Assert.assertEquals(properties.getBlobSize(), result.getBlobSize());
      Assert.assertEquals(properties.getContentType(), result.getContentType());
      Assert.assertEquals(properties.getCreationTimeInMs(), result.getCreationTimeInMs());
      Assert.assertEquals(properties.getOwnerId(), result.getOwnerId());
      Assert.assertEquals(properties.getServiceId(), result.getServiceId());

      // corrupt blob property V1 record
      stream.flip();
      stream.put(10, (byte) 10);
      try {
        BlobProperties resultCorrupt =
            MessageFormatRecord.deserializeBlobProperties(new ByteBufferInputStream(stream)).getBlobProperties();
        Assert.assertEquals(true, false);
      } catch (MessageFormatException e) {
        Assert.assertEquals(e.getErrorCode(), MessageFormatErrorCodes.Data_Corrupt);
      }

      // Test delete V1 record
      ByteBuffer deleteRecord = ByteBuffer.allocate(MessageFormatRecord.Delete_Format_V1.getDeleteRecordSize());
      MessageFormatRecord.Delete_Format_V1.serializeDeleteRecord(deleteRecord, true);
      deleteRecord.flip();
      boolean deleted = MessageFormatRecord.deserializeDeleteRecord(new ByteBufferInputStream(deleteRecord));
      Assert.assertEquals(deleted, true);

      // corrupt delete V1 record
      deleteRecord.flip();
      deleteRecord.put(10, (byte) 4);
      try {
        boolean corruptDeleted = MessageFormatRecord.deserializeDeleteRecord(new ByteBufferInputStream(deleteRecord));
        Assert.assertEquals(true, false);
      } catch (MessageFormatException e) {
        Assert.assertEquals(e.getErrorCode(), MessageFormatErrorCodes.Data_Corrupt);
      }

      // Test message header V1
      ByteBuffer header = ByteBuffer.allocate(MessageFormatRecord.MessageHeader_Format_V1.getHeaderSize());
      MessageFormatRecord.MessageHeader_Format_V1.serializeHeader(header, 1000, 10, -1, 20, 30);
      header.flip();
      MessageFormatRecord.MessageHeader_Format_V1 format = new MessageFormatRecord.MessageHeader_Format_V1(header);
      Assert.assertEquals(format.getMessageSize(), 1000);
      Assert.assertEquals(format.getBlobPropertiesRecordRelativeOffset(), 10);
      Assert.assertEquals(format.getUserMetadataRecordRelativeOffset(), 20);
      Assert.assertEquals(format.getBlobRecordRelativeOffset(), 30);

      // corrupt message header V1
      header.put(10, (byte) 1);
      format = new MessageFormatRecord.MessageHeader_Format_V1(header);
      try {
        format.verifyHeader();
        Assert.assertEquals(true, false);
      } catch (MessageFormatException e) {
        Assert.assertEquals(e.getErrorCode(), MessageFormatErrorCodes.Data_Corrupt);
      }

      // Test usermetadata V1 record
      ByteBuffer usermetadata = ByteBuffer.allocate(1000);
      new Random().nextBytes(usermetadata.array());
      ByteBuffer output =
          ByteBuffer.allocate(MessageFormatRecord.UserMetadata_Format_V1.getUserMetadataSize(usermetadata));
      MessageFormatRecord.UserMetadata_Format_V1.serializeUserMetadataRecord(output, usermetadata);
      output.flip();
      ByteBuffer bufOutput =
          MessageFormatRecord.deserializeUserMetadata(new ByteBufferInputStream(output)).getUserMetadata();
      Assert.assertArrayEquals(usermetadata.array(), bufOutput.array());

      // corrupt usermetadata record V1
      output.flip();
      Byte currentRandomByte = output.get(10);
      output.put(10, (byte) (currentRandomByte + 1));
      try {
        MessageFormatRecord.deserializeUserMetadata(new ByteBufferInputStream(output)).getUserMetadata();
        Assert.assertEquals(true, false);
      } catch (MessageFormatException e) {
        Assert.assertEquals(e.getErrorCode(), MessageFormatErrorCodes.Data_Corrupt);
      }

      // Test blob record V1
      ByteBuffer data = ByteBuffer.allocate(2000);
      new Random().nextBytes(data.array());
      long size = MessageFormatRecord.Blob_Format_V1.getBlobRecordSize(2000);
      ByteBuffer sData = ByteBuffer.allocate((int) size);
      MessageFormatRecord.Blob_Format_V1.serializePartialBlobRecord(sData, 2000);
      sData.put(data);
      Crc32 crc = new Crc32();
      crc.update(sData.array(), 0, sData.position());
      sData.putLong(crc.getValue());
      sData.flip();
      BlobOutput outputData = MessageFormatRecord.deserializeBlob(new ByteBufferInputStream(sData)).getBlobOutput();
      Assert.assertEquals(outputData.getSize(), 2000);
      byte[] verify = new byte[2000];
      outputData.getStream().read(verify);
      Assert.assertArrayEquals(verify, data.array());

      // corrupt blob record V1
      sData.flip();
      currentRandomByte = sData.get(10);
      sData.put(10, (byte) (currentRandomByte + 1));
      try {
        MessageFormatRecord.deserializeBlob(new ByteBufferInputStream(sData));
        Assert.assertEquals(true, false);
      } catch (MessageFormatException e) {
        Assert.assertEquals(e.getErrorCode(), MessageFormatErrorCodes.Data_Corrupt);
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }
  }
}
