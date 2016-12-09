import { ColorPalette, createStyleSheet } from '../../base/styles';

/**
 * The style of the conference UI (component).
 */
export const styles = createStyleSheet({
    /**
     * Avatar style.
     */
    avatar: {
        flex: 1,
        width: '100%'
    },

    /**
     * Conference style.
     */
    conference: {
        alignSelf: 'stretch',
        backgroundColor: ColorPalette.appBackground,
        flex: 1
    },

    /**
     * ParticipantView style
     */
    participantView: {
        alignItems: 'stretch',
        flex: 1
    },

    
    /**
     * The toolbar white button icon style.
     */
    whiteIcon: {
        alignSelf: 'center',
        color: ColorPalette.jitsiDarkGrey,
        fontSize: 24,
        color: 'white'
    },


    /**
     * The toolbar button style.
     */
    toolbarButton: {
        alignSelf: 'center',
        borderRadius: 35,
        borderWidth: 0,
        flexDirection: 'row',
        height: 60,
        justifyContent: 'center',
        width: 60,
        backgroundColor: 'white',
        marginLeft: 20,
        marginRight: 20,
        opacity: 0.8,
        //backgroundColor:ColorPalette.jitsiRed
    },

    toolbarContainer: {
        flex: 1,
        flexDirection: 'row',
        left: 0,
        position: 'absolute',
        right: 0,
        bottom: 0,
        top: 0
    },

});
