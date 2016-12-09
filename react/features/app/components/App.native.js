import React from 'react';
import { Provider } from 'react-redux';
import { AbstractApp } from './AbstractApp';
import {Conference} from "../../conference"


/**
 * Root application component.
 *
 * @extends AbstractApp
 */
export class App extends AbstractApp {
    /**
     * Initializes a new App instance.
     *
     * @param {Object} props - The read-only React Component props with which
     * the new instance is to be initialized.
     */
    constructor(props) {
        super(props);
    }

    /**
     * Subscribe to notifications about activating URLs registered to be handled
     * by this app.
     *
     * @inheritdoc
     * @see https://facebook.github.io/react-native/docs/linking.html
     * @returns {void}
     */
    componentWillMount() {
        super.componentWillMount();
    }

    /**
     * Unsubscribe from notifications about activating URLs registered to be
     * handled by this app.
     *
     * @inheritdoc
     * @see https://facebook.github.io/react-native/docs/linking.html
     * @returns {void}
     */
    componentWillUnmount() {
        super.componentWillUnmount();
    }

    /**
     * Implements React's {@link Component#render()}.
     *
     * @inheritdoc
     * @returns {ReactElement}
     */
    render() {
        const store = this.state.store;
        /* eslint-disable brace-style, react/jsx-no-bind */
        return (
            <Provider store = { store }>
                <Conference isInitiator={false} store= {store} />
            </Provider>
        );

        /* eslint-enable brace-style, react/jsx-no-bind */
    }
}

/**
 * App component's property types.
 *
 * @static
 */
App.propTypes = AbstractApp.propTypes;
