import React, { useState, useEffect } from "react";
import { useToasts } from 'react-toast-notifications';

import { getCurTimeStr, queryAnnotation } from "./utils"
import ListContainer from "./listcontainer"
import { GifBlock } from "./gif_blocks";


function Annotate() {
  const { addToast } = useToasts();
  const [items, setItems] = useState([])

  function handleShowMoreClick() {
    queryAnnotation("false", 10, items[items.length-1].createTime, handleResponse)
  }

  function handleResponse(response) {
    if (response.data.Count > 0){
      setItems([...items, ...response.data.Items])
    } else {
      addToast("No more gifs.", {
        appearance: 'info',
        autoDismiss: true,
        autoDismissTimeout: 2000,
        placement: "bottom-center"
    })
    }
  }

  useEffect(() => {
    queryAnnotation("false", 10, getCurTimeStr(), handleResponse)
  }, []);


  function renderGifBlock(src) {
      return <GifBlock gifItem={src}/>
  }

  return (
    <div className="annotate">        
            <h1 >Annotate</h1>
            <ListContainer 
            columns = {4}
            items={items} 
            onShowMoreClick={handleShowMoreClick}
            showmore={true}
            render={renderGifBlock}
            />
    </div>
  );
}

export default Annotate;