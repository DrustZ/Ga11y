# pip install boto3
import logging
import boto3
import datetime
from botocore.exceptions import ClientError

ACCESS_KEY = "YOUR_ACCESS_KEY"
SECRET_KEY = "YOUR_SECRET_KEY"

s3 = boto3.client(
    's3',
    aws_access_key_id=ACCESS_KEY,
    aws_secret_access_key=SECRET_KEY
)

dynamodb = boto3.resource(
    'dynamodb',
    region_name='us-west-2',
    aws_access_key_id=ACCESS_KEY,
    aws_secret_access_key=SECRET_KEY
)

gifTable = dynamodb.Table('GifItemTable')

def upload_file_wpath(file_name, object_name=None, bucket='gifaccessibilitybucket'):
    """Upload a file to an S3 bucket

    :param file_name: File to upload
    :param bucket: Bucket to upload to
    :param object_name: S3 object name. If not specified then file_name is used
    :return: True if file was uploaded, else False
    """

    # If S3 object_name was not specified, use file_name
    if object_name is None:
        object_name = file_name

    print(object_name)
    # Upload the file
    try:
        if file_name.endswith(".jpg"):
            response = s3.upload_file(file_name, bucket, object_name)
        else:
            response = s3.upload_file(file_name, bucket, object_name, ExtraArgs={'ContentType': "video/mp4"})
    except ClientError as e:
        logging.error(e)
        return False
    return True

#upload file
def upload_file_binary(f, object_name, bucket='gifaccessibilitybucket'):
    s3.upload_fileobj(f, bucket, object_name)

def create_dynamo_entry(gifID, source, annotated="false", isgif="true", annotation=""):
    response = gifTable.put_item(
        Item={'annotated':annotated,
              'createTime':gifID,
              'annotation':annotation,
              'sourceAddress':source,
              'isGif':isgif
    })
    # {'ResponseMetadata': {'RequestId', 'HTTPStatusCode':200
    return response

def get_dynamo_item(gifID):
    try:
        response = gifTable.get_item(Key={'annotated': 'false', 'createTime': gifID})
    except ClientError as e:
        print(e.response['Error']['Message'])
    else:
        if 'Item' in response:
            return response['Item']
            
    try:
        response = gifTable.get_item(Key={'annotated': 'true', 'createTime': gifID})
    except ClientError as e:
        print(e.response['Error']['Message'])
    else:
        if 'Item' in response:
            return response['Item']
    return None
