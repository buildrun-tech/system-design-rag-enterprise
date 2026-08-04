import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import NotebooksPage from './pages/NotebooksPage'
import WorkspacePage from './pages/WorkspacePage'
import ProtectedRoute from './components/ProtectedRoute'

function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/notebooks"
        element={
          <ProtectedRoute>
            <NotebooksPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notebooks/:notebookId"
        element={
          <ProtectedRoute>
            <WorkspacePage />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default App
