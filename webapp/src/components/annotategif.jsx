import React, { useState, useEffect } from "react";
import { useLocation } from 'react-router-dom'
import { AnnotateBlock } from "./gif_blocks";
import { getCurTimeStr, queryAnnotation } from "./utils"

function AnnotateGif(props) {
    const [item, setItem] = useState([])
    let location = useLocation()

    function handleResponse(response) {
        if (response.data.Count > 0){
            setItem([response.data.Items[0]])
        }
    }

    useEffect(() => {
        if (location.state === undefined){
            //request a random gif to annotate
            queryAnnotation("false", 1, getCurTimeStr(), handleResponse)
        } else {
            setItem([location.state.data])
        }
    }, []);

    return (
        <div className="annotate">
            <h1 >AnnotateGif</h1>
            {(item.length > 0) ? 
            <AnnotateBlock gifItem={item[0]} />
            : null }
        </div>
    );
}

export default AnnotateGif;