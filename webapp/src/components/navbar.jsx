import React from "react"
import { Menu } from 'semantic-ui-react'
import { NavLink, withRouter } from "react-router-dom"
import { UploadModal } from "./uploadmodal"

function Navigation(props) {

    return (
        <div className="navigation">

            <Menu secondary>
            <Menu.Item header>
                <NavLink className="navbar-brand" to="/">
                    Gif Annotator
                </NavLink>
            </Menu.Item>

            <Menu.Item
                as={NavLink} to="/annotate"
                name='Annotate'
                active={props.location.pathname === '/annotate'}
            />
            <Menu.Item
                as={NavLink} to="/browse"
                name='Browse'
                active={props.location.pathname === '/browse'}
            />
            <Menu.Item
                as={NavLink} to="/about"
                name='About'
                active={props.location.pathname === '/about'}
            />

            <Menu.Item>
                <UploadModal />
            </Menu.Item>
            </Menu>
        </div>
    )
}

export default withRouter(Navigation);