import React, { useState, useEffect } from 'react'
import { Link, useHistory } from "react-router-dom";
import { Container, Form, Grid, Image, Segment, Divider} from 'semantic-ui-react'
import { useToasts } from 'react-toast-notifications';

import { getGifUrlPrefix, updateAnnotation, addAnnotation } from './utils'
import '../style.css'

//for single annotation on the homepage
export function AnnotateBlock(props) {
    const [annotation, setAnnotation] = useState("")
    const { addToast } = useToasts();
    const history = useHistory();

    useEffect(() => {
        setAnnotation(props.gifItem.annotation)
    }, [props.gifItem]);

    function handleInputChange(event) {
        setAnnotation(event.target.value)
    }
    
    function handleClick() {
        console.log("Annotation: " + annotation);
        if (annotation.trim().length === 0) {
            //empty string
            addToast("Annotation cannot be empty.", {
                appearance: 'error',
                autoDismiss: true,
                autoDismissTimeout: 2000,
                placement: "bottom-center"
            })
        } else {
            addAnnotation(annotation, props.gifItem.createTime,
                props.gifItem.sourceAddress, props.gifItem.isGif,
                response => { 
                    //after succeed, we go back to home page
                    history.push({pathname: "/"})
                 })
            addToast("Annotation added.", {
                appearance: 'success',
                autoDismiss: true,
                autoDismissTimeout: 2000,
                placement: "bottom-center"
            })
        }
    }

    return (
        <Segment>
        <Container>
            <p>
            <b>Instructions:</b> Write down descriptions of the GIF so that someone can 
            understand its content even without seeing the GIF. Do not include any 
            subjective opinions, just an objective description.
            </p>

            <ul>
                <li>Please describe the visual content and the related information 
                    that is helpful for understanding the GIF.</li>
                <li>Please describe any actors (people, animals, etc.), their actions
                    and expressions, the activities underway, and the environment in 
                    which those activities are taking place. </li>
                <li>If this GIF contains clips or actors from movies, television, 
                    or similar sources, please describe that information.</li>
                <li>If there is text in the gif, please describe it.</li>
                <li>Please keep your description concise overall.</li>
                <li>Please use a minimum of 8 words.</li>
            </ul>
        </Container>
        <Grid>
            <Grid.Column width={8}>
            {props.gifItem.isGif === 'true' ? 
            <video preload="auto" className="gif" playsInline 
                autoPlay loop muted src={getGifUrlPrefix()+props.gifItem.sourceAddress} 
                type="video/webm" />
            : <Image src={props.gifItem.sourceAddress} centered size='medium'/>
            }
            </Grid.Column>

            <Grid.Column width={8}>
            <Form>
                <Form.TextArea
                label='Annotate this Gif'
                rows={6}
                onChange={handleInputChange}
                value={annotation}
                placeholder='Write down the annotation'
                />
                <Form.Button positive onClick={handleClick}>
                    Submit
                </Form.Button>
            </Form>
            </Grid.Column>
        </Grid>
        <Divider />
        {/* Example */}
        <Grid>
            <Grid.Column width={8}>
            <Image src="https://c.tenor.com/ZIGOfTXeYvkAAAAC/deal-with-it-squirtle.gif" centered size='small'/>
            </Grid.Column>

            <Grid.Column width={8}>
                <Container>
                    <p>
                    <b>Example Annotation</b> Squirtle turns its face to the camera, and
                    puts on cool sunglasses. Its face looks stubborn and unwilling to give
                    up. There are also two other pokemons in the background. The text in 
                    the GIF says "Deal with it". Squirtle is a turtle pokemon. The GIF can 
                    be used to comfort someone when they encounter some hardship or 
                    unsatisfactory results, to let them accept the reality.
                    </p>
                </Container>
            </Grid.Column>
        </Grid>
        </Segment>
   )
}

//for annotation page browse
export function GifBlock(props) {
    let history = useHistory();

    function handleClick() {
        history.push({
            pathname: "/annotategif",
            state:{
                data: props.gifItem
            }
        })
    }

    return (
        <Segment>
        <Grid onClick={handleClick}>
            {props.gifItem.isGif === 'true' ? 
            <video preload="auto" className="gif" playsInline 
                autoPlay loop muted src={getGifUrlPrefix()+props.gifItem.sourceAddress} 
                type="video/webm" />
            : <Image src={props.gifItem.sourceAddress} centered size='medium'/>
            }
        </Grid>
        </Segment>
   )
}


//for browsing & editing
export function BrowseBlock(props) {
    const { addToast } = useToasts();
    const [annotation, setAnnotation] = useState("")
    const [originalAnno, setOriginalAnno] = useState("")
    const [isEditing, setIsEditing] = useState(false)

    useEffect(() => {
        setOriginalAnno(props.gifItem.annotation)
        setAnnotation(props.gifItem.annotation)
    }, [props.gifItem]);

    function handleInputChange(event) {
        if (isEditing) {
            setAnnotation(event.target.value)
        }
    }
    
    function handleClick(event) {
        if (event.target.id === 'cancelbtn') {
            setAnnotation(originalAnno)
            setIsEditing(false)
        } else {
            if (isEditing) {
                //do some validation work
                setIsEditing(false)
                if (annotation.trim().length == 0) {
                    //empty string
                    addToast("Annotation cannot be empty.", {
                        appearance: 'error',
                        autoDismiss: true,
                        autoDismissTimeout: 2000,
                        placement: "bottom-center"
                    })
                } else {
                    updateAnnotation(annotation, props.gifItem.createTime,
                        response => { console.log(response) })
                    addToast("Annotation updated.", {
                        appearance: 'success',
                        autoDismiss: true,
                        autoDismissTimeout: 2000,
                        placement: "bottom-center"
                    })
                }
            } else {
                setIsEditing(true)
            }
        }
    }

    return (
        <Segment>
        <Grid>
            <Grid.Column width={8}>
                {props.gifItem.isGif === 'true' ? 
                <video preload="auto" className="gif" playsInline 
                    autoPlay loop muted src={getGifUrlPrefix()+props.gifItem.sourceAddress} 
                    type="video/webm" />
                : <Image src={props.gifItem.sourceAddress} centered size='medium'/>
                }
            </Grid.Column>

            <Grid.Column width={8}>
            <Form>
                <Form.TextArea
                label='Annotate This Gif'
                onChange={handleInputChange}
                value={annotation}
                />
                {isEditing ?
                    <Form.Button color='grey' onClick={handleClick} id='cancelbtn'>
                        Cancel
                    </Form.Button> 
                : null}
                <Form.Button color='orange' onClick={handleClick} id='editbtn'>
                    {isEditing ? 'Submit' : 'Edit'}
                </Form.Button>
            </Form>
            </Grid.Column>
        </Grid>
        </Segment>
   )
}