import React, { useState, useEffect } from 'react'
import { Grid, Button, Segment } from 'semantic-ui-react'

// A component for displaying items in list
function ListContainer(props) {
    const [items, setItems] = useState([])

    useEffect(() => {
        setItems(props.items)
    }, [props.items]);

    function handleShowMoreClick() {
        props.onShowMoreClick()
    }

    function renderGrids() {
        return items.map((itm, i) => {
            return <Grid.Column key={i}>
                {/* render using props https://reactjs.org/docs/render-props.html */}
                {/* set the render prop of ListContianer as a function */}
                {props.render(itm)}
            </Grid.Column>
        })
    }

    return (
        <Segment raised>
            <Grid stackable columns={props.columns}>
                {renderGrids()}
            </Grid>
            {props.showmore ? <Button onClick={handleShowMoreClick}>Show More</Button> : null}
        </Segment>
   )
}

export default ListContainer;
