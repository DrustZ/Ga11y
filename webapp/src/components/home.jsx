import React, {useState, useEffect} from "react";
import { getCurTimeStr, queryAnnotation } from "./utils"
import { AnnotateBlock } from "./gif_blocks";

function Home() {
  const [annotateItm, setAnnotateItm] = useState([])

  function handleResponse(response) {
    if (response.data.Count > 0){
      setAnnotateItm([response.data.Items[0]])
    }
  }

  useEffect(() => {
    queryAnnotation("false", 1, getCurTimeStr(), handleResponse)
  }, []);

  return (
    <div className="home">      
            <h1 className="font-weight-light">Home</h1>
            <p>
              Welcome to Gif Annotator
            </p>
            {(annotateItm.length > 0) ? 
            <AnnotateBlock gifItem={annotateItm[0]}/>
            : null}
    </div>
  );
}

export default Home;