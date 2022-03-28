# The annotation server app

`server.py` is the root file (contains the server and the logical process of the request)

The server will receive request sent from the annotation web app and the android client app.

For client gif request, the client app will zip the gif screenshots together with a json meta file,
which contains the count of the screen-shots and the bounding box (if exists) of the gif element in the screenshots.

API-related files: `GcloudVisionApi.py`, `awsDB.py`

Elasticsearch database files (from the [image-match](https://github.com/ProvenanceLabs/image-match) repo): `elasticsearch_driver.py` and `signature_database_base.py`

