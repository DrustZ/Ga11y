import axios from 'axios';

var myAWSDynamoDBprefix = "https://abka732fx9.execute-api.us-west-2.amazonaws.com/default/dynamodbQuests"

export function getGifUrlPrefix() {
    return 'https://d1ld7v3odkfp77.cloudfront.net/'
}

export function getCurTimeStr() {
    return (new Date()).toISOString().slice(0, 19).replace(/-/g, "").replace("T", "_").replace(/:/g,"")+"_000000";
}

export function queryAnnotation(annotated, limit, tilltime, callback) {
    console.log("tilltime: "+tilltime)
    axios.post(myAWSDynamoDBprefix, {
      quest: 'query',
      annotated: annotated,
      limit: limit,
      tilltime: tilltime
    })
    .then(function (response) {
      callback(response)
    })
    .catch(error => {
        console.log(error)
    })
}

export function updateAnnotation(annotation, createTime, callback) {
    axios.post(myAWSDynamoDBprefix, {
      quest: 'update',
      update: 'annotation',
      annotation: annotation,
      createTime: createTime
    })
    .then(function (response) {
      callback(response)
    })
    .catch(error => {
        console.log(error)
    })
}

export function addAnnotation(annotation, createTime, sourceAddress, isGif, callback) {
    axios.post(myAWSDynamoDBprefix, {
      quest: 'update',
      update: 'newannotate',
      isGif: isGif,
      annotation: annotation,
      createTime: createTime,
      sourceAddress: sourceAddress
    })
    .then(function (response) {
      callback(response)
    })
    .catch(error => {
        console.log(error)
    })
}