import React, { useState, useEffect } from "react";
import { useToasts } from 'react-toast-notifications';

import { getCurTimeStr, queryAnnotation } from "./utils"
import ListContainer from "./listcontainer"
import { BrowseBlock } from "./gif_blocks";

function Browse() {
  const { addToast } = useToasts();
  const [items, setItems] = useState([])

  function handleShowMoreClick() {
    queryAnnotation("true", 10, items[items.length-1].createTime, handleResponse)
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
    queryAnnotation("true", 10, getCurTimeStr(), handleResponse)
  }, []);

  function renderBrowseBlock(src) {
      return <BrowseBlock gifItem={src}/>
  }

  return (
    <div className="annotate">        
            <h1 >Annotate</h1>
            <ListContainer 
            columns={2}
            items={items} 
            onShowMoreClick={handleShowMoreClick}
            showmore={true}
            render={renderBrowseBlock}
            />
    </div>
  );
}

export default Browse;