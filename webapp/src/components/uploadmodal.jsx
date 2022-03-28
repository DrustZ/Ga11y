
import React, { useCallback, useState } from 'react'
import axios from 'axios';
import { useDropzone } from 'react-dropzone'
import { Button, Header, Modal, Grid, Input, 
    Message, Form, Segment, Image } from 'semantic-ui-react'
import { useToasts } from 'react-toast-notifications';
import '../style.css'

export function UploadModal() {
    const [open, setOpen] = React.useState(false)
    const [inputurl, setInputurl] = React.useState('')
    const [urlerror, setUrlerror] = React.useState(false)
    const [cansubmit, setCansubmit] = React.useState(true)
    const [gifsrc, setGifsrc] = React.useState('')
    const [annotation, setAnnotation] = useState("")
    const { addToast } = useToasts();

    function resetStatus(){
        setInputurl('')
        setUrlerror('')
        setGifsrc('')    
    }

    function handleImageUpload(data){
        // console.log("data upload: "+data)
        setGifsrc(data) 
    }

    function handleInputChange(event) {
        setAnnotation(event.target.value)
    }

    function uploadToServer() {
        axios.post('http://is-mingrui.ischool.uw.edu:8080/upload', {
            filetype: 'gif',
            file: gifsrc,
            annotation: annotation
        }).then(result => {
            if (result.data.status === 'exist'){
                addToast("Gif already exist in the database.", {
                    appearance: 'info',
                    autoDismiss: true,
                    autoDismissTimeout: 2000,
                    placement: "bottom-center"
                })
            } else {
                addToast("Gif uploaded.", {
                    appearance: 'success',
                    autoDismiss: true,
                    autoDismissTimeout: 2000,
                    placement: "bottom-center"
                })
            }})
        .catch(err => console.log(err))
    }

    function handleButtonClick(e) {
        if (e.target.id === 'submitbtn'){
            //if empty 
            if (annotation.trim().length === 0){
                setCansubmit(false)
                return
            }
            uploadToServer()
            setCansubmit(true)
            
            setAnnotation('')
            setGifsrc('')
            setUrlerror('')
            setOpen(false)
        } else if (e.target.id === 'urlbtn'){
            console.log("inputurl: "+inputurl)
            var gifurl = inputurl
            //ensure there's http(s)
            if (!/^https?:\/\//i.test(gifurl)) {
                gifurl = 'http://' + gifurl;
            }
            axios.get(gifurl)
                .then(result => {
                    console.log("response: "+result)
                    if (result.headers['content-type'].includes('gif')){
                        console.log("valid gif url!")
                        console.log(result)
                        setGifsrc(gifurl)
                    } else {
                        setUrlerror(true)
                    }})
                .catch(error => {
                    setUrlerror(true)
                });
        }
    }

    function handleChange(e) {
        setInputurl(e.target.value)
    }

    function displayImageOrHeader() {
        if (gifsrc === ''){
            return <>
            <Header>Please upload a gif file</Header>
            {/* <Grid.Row>
                <Input 
                className = {urlerror ? 'error' : null}
                placeholder='Url'
                value={inputurl}
                onChange={handleChange}
                />
                <Button 
                id="urlbtn" 
                onClick={handleButtonClick}>Proceed</Button>
            </Grid.Row> */}
            <MyDropzone uploadCallback={handleImageUpload}/>
            </>
        } else {
            return <Segment>
            <Grid>
                <Grid.Column width={8}>
                <Image src={gifsrc} centered size='medium'/>
                </Grid.Column>

                <Grid.Column width={8}>
                <Form>
                    <Form.TextArea
                    label='Annotation This Gif'
                    onChange={handleInputChange}
                    value={annotation}
                    placeholder='Write down the annotation'
                    />
                    { cansubmit ? null :
                      <Message color='red'>Please provide the annotation</Message>}
                    <Form.Button id="submitbtn" positive onClick={handleButtonClick}>
                        Submit
                    </Form.Button>
                </Form>
                </Grid.Column>
            </Grid>
            </Segment>
        }
    }

    return (
        <Modal
        closeIcon
        onClose={() => setOpen(false)}
        onOpen={() => {
            setOpen(true)
            resetStatus()
        }}
        open={open}
        trigger={<Button>Upload</Button>}
        >

        <Modal.Header>Upload a GIF</Modal.Header>
        <Modal.Content>
            <Modal.Description>
            {displayImageOrHeader()}
            </Modal.Description>
        </Modal.Content>
        </Modal>
    )
}


function MyDropzone(props) {
  const onDrop = useCallback((acceptedFiles) => {
    acceptedFiles.forEach((file) => {
      const reader = new FileReader()

      reader.onabort = () => console.log('file reading was aborted')
      reader.onerror = () => console.log('file reading has failed')
      reader.onload = (ev) => {
      // Do whatever you want with the file contents
        const binaryStr = reader.result
        console.log(binaryStr)
        props.uploadCallback(ev.target.result)

      }
      reader.readAsDataURL(file)
    })
  }, [props])
  const {getRootProps, getInputProps} = useDropzone(
      {accept: 'image/gif',
       onDrop: onDrop})

  return (
    <div  className="dropzone" {...getRootProps()}>
      <input {...getInputProps()} />
      <p>Drag 'n' drop some files here, or click to select files</p>
    </div>
  )
}