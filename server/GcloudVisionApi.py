#need to install cloud vision python lib
from google.cloud import vision
from PIL import Image
import io

#Azure for captioning
from azure.cognitiveservices.vision.computervision import ComputerVisionClient
from azure.cognitiveservices.vision.computervision.models import OperationStatusCodes
from azure.cognitiveservices.vision.computervision.models import VisualFeatureTypes
from msrest.authentication import CognitiveServicesCredentials

class GcloudVisionApi:
    def __init__(self):
        super().__init__()
        # have your google api key ready in the file "gapiKey.json" 
        self.client = vision.ImageAnnotatorClient.from_service_account_json('gapiKey.json')

        #Azure keys, for getting image description
        subscription_key = "YOUR_Azure_KEY"
        endpoint = "https://gifcaptiona11y.cognitiveservices.azure.com/"
        self.computervision_client = ComputerVisionClient(endpoint, CognitiveServicesCredentials(subscription_key))

    #img: an nd array
    def understandImage(self, img):
        """Detects web annotations given an image."""
        img_byte_arr = io.BytesIO()
        img_test = Image.fromarray(img)
        img_test.save(img_byte_arr, format='PNG')
        rawimg = img_byte_arr.getvalue()
        img_byte_arr.seek(0)

        image = vision.Image(content=rawimg)

        resdict = {}

        #first, understand the content with guess
        response = self.client.web_detection(image=image)
        annotations = response.web_detection

        if annotations.best_guess_labels:
            for label in annotations.best_guess_labels:
                # print('\nBest guess label: {}'.format(label.label))
                resdict['best_guess'] = label.label
                break

        #second, detect objects
        response = self.client.label_detection(image=image)
        labels = response.label_annotations

        cnt = 0
        lbs = []
        for label in labels:
            # print(label.description)
            lbs.append(label.description)
            cnt += 1
            if cnt == 3:
                break
        resdict['labels'] = lbs

        #third, ocr
        response = self.client.text_detection(image=image)
        texts = response.text_annotations
        # print('Texts:')

        for text in texts:
            # print('\n"{}"'.format(text.description))
            resdict['text'] = text.description
            break

        if response.error.message:
            raise Exception(
                '{}\nFor more info on error messages, check: '
                'https://cloud.google.com/apis/design/errors'.format(
                    response.error.message))

        description_result = self.computervision_client.describe_image_in_stream(img_byte_arr)
        if len(description_result.captions) > 0:
            caption = description_result.captions[0] 
            resdict['caption'] = caption.text

        return resdict