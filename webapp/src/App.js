import React from "react";
import { BrowserRouter as Router, Route, Switch } from "react-router-dom";
import { Navigation, Home, About, Annotate, Browse, AnnotateGif } from "./components";
import { ToastProvider } from 'react-toast-notifications';

function App() {
  return (
    <ToastProvider>
    <div className="App">
      <Router>
        <Navigation />
        <Switch>
          <Route path="/" exact component={() => <Home />} />
          <Route path="/annotate" exact component={() => <Annotate />} />
          <Route path="/browse" exact component={() => <Browse />} />
          <Route path="/about" exact component={() => <About />} />
          <Route path="/annotategif" exact component={(props) => <AnnotateGif props={props}/>} />
        </Switch>
      </Router>
    </div>
    </ToastProvider>
  );
}

export default App;