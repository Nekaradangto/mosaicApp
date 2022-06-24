from __future__ import absolute_import, division, print_function, unicode_literals
from flask import Flask, render_template, request, send_file
import pytesseract
import cv2
import numpy as np
from PIL import Image, ImageFilter
import predict
from pathlib import Path
app = Flask(__name__)

app.config['JSON_AS_ASCII'] = False
pytesseract.pytesseract.tesseract_cmd="/usr/bin/tesseract"

@app.route('/')
def hello_world():
    return 'Hello World!'

@app.route('/test')
def test():
    return render_template('post.html')

@app.route('/updateimage', methods=['POST'])
def updateimage():
    # 이미지 파일 가져오기
    params = request.files['image/jpg'].read()
    
    # tesseract로 좌표 저장
    encoded_img = np.fromstring(params, dtype = np.uint8)
    t_img = cv2.imdecode(encoded_img, cv2.IMREAD_COLOR)

    cv2.imwrite('filteredImage.jpg', t_img)

    img=Image.open('filteredImage.jpg')

    h, w, c=t_img.shape
    
    ocr_text=pytesseract.image_to_string(img, lang='kor')

    with open("sample_pred_in.txt", "w") as file:
        for text in ocr_text:
            file.write(text)

    # NER 결과 output 파일에 저장
    predict.main()

    # pytesseract에서 해당 글자의 좌표 값 출력
    ocr_info=pytesseract.image_to_boxes(img, lang='kor')

    f = open("sample_pred_out.txt", 'r')
    words = f.readlines()

    ocr_info_list=ocr_info.split("\n")
    
    ocr_string=''

    for ocr_num in range(len(ocr_info_list)-1):
        ocr_string+=list(ocr_info_list[ocr_num])[0]

    ocr_indices=[]
    before_start_index=0
    for word in words:
        tmp_str=word.split()[0]
        start_index=ocr_string.find(tmp_str, before_start_index)

        now_indices=[start_index, start_index+len(tmp_str)]
        ocr_indices.append(now_indices) # start, end of index

        print(ocr_string[start_index:(start_index+len(tmp_str))], " : ", start_index)

        before_start_index=start_index

    word_arr=[]

    for index in ocr_indices:
        word_index={'x':0, 'y':0, 'width':0, 'height':0}

        start_tmp_list=ocr_info_list[index[0]].split()
        end_tmp_list=ocr_info_list[index[1]-1].split()

        word_index['x']=int(start_tmp_list[1])
        word_index['y']=h-int(start_tmp_list[2])
        word_index['width']=int(end_tmp_list[3])
        word_index['height']=h-int(end_tmp_list[4])

        print(word_index)

        word_arr.append(word_index)

    # draw rect
    for index in word_arr:
        cropped_image=img.crop((index['x'],index['height'],index['width'],index['y']))
        blurred_image=cropped_image.filter(ImageFilter.GaussianBlur(radius=20))
        img.paste(blurred_image,(index['x'],index['height'],index['width'],index['y']) )

    img.save('filteredimg.jpg')
    return send_file('filteredimg.jpg')


@app.route('/postimage', methods=['POST'])
def postimage():
    
    # 이미지 파일 가져오기
    params = request.files['image/jpg'].read()
    
    # tesseract로 좌표 저장
    encoded_img = np.fromstring(params, dtype = np.uint8)
    img = cv2.imdecode(encoded_img, cv2.IMREAD_COLOR)
    #cv2.imwrite('test2.jpg', img)
    #Img=Image.open('test2.jpg')
    h, w, c=img.shape

    print("Width: ", w, "Height: ", h)
 
    ocr_text=pytesseract.image_to_string(img, lang='kor')

    with open("sample_pred_in.txt", "w") as file:
        for text in ocr_text:
            file.write(text)

    # NER 결과 output 파일에 저장
    predict.main()

    # pytesseract에서 해당 글자의 좌표 값 출력
    ocr_info=pytesseract.image_to_boxes(img, lang='kor')

    f = open("sample_pred_out.txt", 'r')
    words = f.readlines()

    ocr_info_list=ocr_info.split("\n")
    
    ocr_string=''

    for ocr_num in range(len(ocr_info_list)-1):
        ocr_string+=list(ocr_info_list[ocr_num])[0]

    ocr_indices=[]
    before_start_index=0
    for word in words:
        tmp_str=word.split()[0]
        start_index=ocr_string.find(tmp_str, before_start_index)

        now_indices=[start_index, start_index+len(tmp_str)]
        ocr_indices.append(now_indices) # start, end of index

        print(ocr_string[start_index:(start_index+len(tmp_str))], " : ", start_index)

        before_start_index=start_index

    word_arr=[]

    for index in ocr_indices:
        word_index={'x':0, 'y':0, 'width':0, 'height':0}

        start_tmp_list=ocr_info_list[index[0]].split()
        end_tmp_list=ocr_info_list[index[1]-1].split()

        word_index['x']=int(start_tmp_list[1])
        word_index['y']=h-int(start_tmp_list[2])
        word_index['width']=int(end_tmp_list[3])
        word_index['height']=h-int(end_tmp_list[4])

        print(word_index)

        word_arr.append(word_index)

    # draw rect
    # for index in word_arr:
    #     cropped_image=Img.crop((index['x'],index['y'],index['width'],index['height'])
    #     blurred_image=cropped_image.filter(ImageFilter.GaussianBlur(radius=20))
    #     Img.paste(blurred_image,(index['x'],index['y'],index['width'],index['height']) )
        

    #word_indices.(word_arr)
    #print(ocr_info)
    cv2.imwrite('test2.jpg', img)
    print(word_arr)
    return {'indices':word_arr}

if __name__ == '__main__':
    # app.debug = True
    app.run(host='0.0.0.0',  port=8000)